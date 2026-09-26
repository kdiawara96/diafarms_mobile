package com.mobile.diafarms.data;

import android.content.Context;

import com.google.gson.Gson;
import com.mobile.diafarms.models.SaisieLocale;
import com.mobile.diafarms.models.SaisieType;
import com.mobile.diafarms.network.ApiClient;
import com.mobile.diafarms.network.DataApi;
import com.mobile.diafarms.network.dto.AlimentationCreateRequest;
import com.mobile.diafarms.network.dto.ApiEnvelope;
import com.mobile.diafarms.network.dto.ClientCreateRequest;
import com.mobile.diafarms.network.dto.CollecteOeufsCreateRequest;
import com.mobile.diafarms.network.dto.CommandeCreateRequest;
import com.mobile.diafarms.network.dto.ConsommationAlimentCreateRequest;
import com.mobile.diafarms.network.dto.CreatedEntityResponse;
import com.mobile.diafarms.network.dto.MortaliteCreateRequest;
import com.mobile.diafarms.network.dto.ReformeCreateRequest;
import com.mobile.diafarms.network.dto.SalairePayerRequest;
import com.mobile.diafarms.network.dto.SessionPeseeServeur;
import com.mobile.diafarms.network.dto.SessionPeseeSyncRequest;
import com.mobile.diafarms.network.dto.SoinsCreateRequest;
import com.mobile.diafarms.network.dto.TransactionCreateRequest;
import com.mobile.diafarms.network.dto.VenteOeufsCreateRequest;
import com.mobile.diafarms.network.dto.VenteReformeCreateRequest;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Pousse vers diafarms_back les saisies enregistrées localement (statut LOCAL ou
 * ERROR). Traite une saisie à la fois, dans l'ordre, pour rester simple à suivre et
 * éviter toute écriture concurrente sur la base locale.
 */
public class SyncManager {

    /** Résultat d'un envoi. aRenvoyer : saisies restées en attente (réseau, serveur
     * indisponible, saisie en cours de traitement côté serveur) qui repartiront au
     * prochain envoi avec la même clé. authMessage non null : la session n'est plus
     * acceptée (401/403), l'envoi s'est arrêté et l'utilisateur doit se reconnecter ;
     * aucune saisie n'est perdue. */
    public static class Bilan {
        public int envoyees;
        public int refusees;
        public int aRenvoyer;
        public String authMessage;
    }

    public interface SyncCallback {
        default void onProgress(int done, int total) {}
        void onComplete(int success, int failed);
        default void onBilan(Bilan bilan) {
            onComplete(bilan.envoyees, bilan.refusees + bilan.aRenvoyer);
        }
    }

    private final Context appContext;
    private final LocalDatabase localDatabase;
    private final DataApi api;
    private final Gson gson = new Gson();

    public SyncManager(Context context) {
        Context appContext = context.getApplicationContext();
        this.appContext = appContext;
        this.localDatabase = new LocalDatabase(appContext);
        this.api = ApiClient.dataApi(appContext);
    }

    // Compte dont on envoie les saisies, figé au début de l'envoi : le jeton est lu à chaque
    // requête (ApiClient.AuthInterceptor), donc si le compte actif change pendant l'envoi,
    // on s'arrête plutôt que d'envoyer une saisie avec le jeton d'un autre compte.
    private String compteEnvoi;
    private List<SaisieLocale> file;
    private SyncCallback appelant;
    private final Bilan bilan = new Bilan();

    /** N'envoie que les saisies du compte actif (voir LocalDatabase.getPendingSaisies) ;
     * celles d'un autre compte restent en attente jusqu'à sa prochaine connexion ici. */
    public void syncAll(SyncCallback callback) {
        compteEnvoi = SessionManager.activeUserId(appContext);
        file = compteEnvoi != null ? localDatabase.getPendingSaisies() : new java.util.ArrayList<>();
        appelant = callback;
        syncNext(0);
    }

    private void terminer() {
        appelant.onBilan(bilan);
    }

    /** Arrêt anticipé : tout ce qui n'a pas été tenté reste en attente (compté à renvoyer). */
    private void arreter(int index) {
        bilan.aRenvoyer += Math.max(0, file.size() - index);
        terminer();
    }

    private void syncNext(int index) {
        if (index >= file.size()) {
            terminer();
            return;
        }
        if (compteEnvoi == null || !compteEnvoi.equals(SessionManager.activeUserId(appContext))) {
            // Changement de compte pendant l'envoi : le reste attendra (jamais envoyé avec
            // le jeton d'un autre compte).
            arreter(index);
            return;
        }

        SaisieLocale saisie = file.get(index);
        appelant.onProgress(index, file.size());
        if (!compteEnvoi.equals(saisie.getOwnerUserId())) {
            syncNext(index + 1);
            return;
        }

        if (saisie.getType() == SaisieType.PESEE_SESSION) {
            envoyerSessionPesee(index, saisie);
            return;
        }

        Callback<ApiEnvelope<CreatedEntityResponse>> retrofitCallback = new Callback<ApiEnvelope<CreatedEntityResponse>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<CreatedEntityResponse>> call, Response<ApiEnvelope<CreatedEntityResponse>> response) {
                CreatedEntityResponse data = response.isSuccessful() && response.body() != null
                        ? response.body().getData() : null;
                // 2xx, y compris une réponse rejouée par le serveur (Idempotency-Replayed) :
                // la saisie existe côté serveur, même si la première réponse s'était perdue.
                if (data != null && data.getUniqueId() != null) {
                    localDatabase.markSynced(saisie.getLocalId(), data.getUniqueId());
                    bilan.envoyees++;
                    syncNext(index + 1);
                } else {
                    traiterEchec(index, saisie, response.code(), extractServerMessage(response));
                }
            }

            @Override
            public void onFailure(Call<ApiEnvelope<CreatedEntityResponse>> call, Throwable t) {
                traiterEchec(index, saisie, 0, null);
            }
        };

        // Un type que dispatch ne sait pas envoyer ne doit JAMAIS bloquer la suite : avant,
        // aucun callback n'était appelé pour lui (ex: Vente de fientes) et toute la
        // synchronisation restait suspendue. On le marque en erreur et on continue.
        if (!dispatch(saisie, retrofitCallback)) {
            markError(saisie, "Ce type de saisie ne peut pas être envoyé par cette version de l'application", -1);
            bilan.refusees++;
            syncNext(index + 1);
        }
    }

    /**
     * Échec d'un envoi, selon sa nature (voir le contrat d'idempotence) :
     * - réseau (code 0), délai dépassé, 5xx, 409 « en cours de traitement » : la saisie reste
     *   EN ATTENTE avec une petite note, elle repartira au prochain envoi avec la même clé
     *   (le serveur la rejouera si elle était déjà passée). Réseau coupé : on s'arrête là,
     *   inutile d'attendre le délai pour chacune des suivantes ;
     * - 401/403 : la session n'est plus acceptée, rien n'est perdu, l'envoi s'arrête et
     *   l'utilisateur est invité à se reconnecter ;
     * - autre 4xx (400 métier, 422 clé déjà utilisée...) : refus définitif, ERREUR avec le
     *   message du serveur ; il faut corriger la saisie (une nouvelle clé sera alors créée).
     */
    private void traiterEchec(int index, SaisieLocale saisie, int code, String message) {
        if (code == 0 || code == 408 || code == 409 || code == 429 || code >= 500) {
            String note = code == 0 ? "Pas encore envoyée : réseau indisponible, nouvel essai au prochain envoi"
                    : code == 409 ? "Pas encore envoyée : en cours de traitement par le serveur, nouvel essai au prochain envoi"
                    : "Pas encore envoyée : serveur indisponible, nouvel essai au prochain envoi";
            localDatabase.marquerARenvoyer(saisie.getLocalId(), note, code);
            bilan.aRenvoyer++;
            if (code == 0) {
                arreter(index + 1);
            } else {
                syncNext(index + 1);
            }
            return;
        }
        if (code == 401 || code == 403) {
            bilan.authMessage = code == 401 || message == null
                    ? "Votre session n'est plus acceptée par le serveur. Scannez de nouveau votre QR code pour envoyer vos saisies : elles restent enregistrées sur ce téléphone."
                    : message + "\n\nVos saisies restent enregistrées sur ce téléphone. Reconnectez-vous (nouveau scan du QR code) pour les envoyer.";
            arreter(index);
            return;
        }
        markError(saisie, message, code);
        bilan.refusees++;
        syncNext(index + 1);
    }

    /**
     * Session de pesée : envoi de l'instantané complet, puis fusion de l'état renvoyé par le
     * serveur dans l'état local ACTUEL (voir SessionPeseeSyncRequest.fusionner et
     * PeseeServeurSync.appliquer). La session a pu être réécrite pendant l'envoi : ce qui
     * n'a pas été envoyé reste en attente (ligne LOCAL, repart au prochain envoi) ; ligne
     * SYNCED seulement si l'état local est celui du serveur. Nouvelles modifications web
     * et pesées refusées (session terminée sur le web) : notification. Déjà idempotente par
     * ses propres identifiants : pas d'en-tête Idempotency-Key.
     */
    private void envoyerSessionPesee(int index, SaisieLocale saisie) {
        SessionPeseeSyncRequest envoye;
        try {
            envoye = gson.fromJson(saisie.getPayloadJson(), SessionPeseeSyncRequest.class);
        } catch (Exception e) {
            envoye = null;
        }
        if (envoye == null) {
            markError(saisie, "Session de pesée illisible sur ce téléphone", -1);
            bilan.refusees++;
            syncNext(index + 1);
            return;
        }
        final SessionPeseeSyncRequest instantane = envoye;
        api.syncSessionPesee(envoye.pourEnvoi()).enqueue(new Callback<ApiEnvelope<SessionPeseeServeur>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<SessionPeseeServeur>> call, Response<ApiEnvelope<SessionPeseeServeur>> response) {
                SessionPeseeServeur data = response.isSuccessful() && response.body() != null
                        ? response.body().getData() : null;
                if (data != null && data.uniqueId != null) {
                    // Réponse sans détail des pesées (inattendu) : repli sur « le serveur
                    // détient ce qui a été envoyé ».
                    SessionPeseeServeur serveur = data.pesees != null ? data : null;
                    SessionPeseeSyncRequest.Fusion f = PeseeServeurSync.appliquer(appContext, localDatabase,
                            saisie.getLocalId(), instantane, serveur, true);
                    // Restée LOCAL (modifiée pendant l'envoi) : ni envoyée ni en échec.
                    if (f != null && f.aJour) bilan.envoyees++;
                    syncNext(index + 1);
                } else {
                    traiterEchec(index, saisie, response.code(), extractServerMessage(response));
                }
            }

            @Override
            public void onFailure(Call<ApiEnvelope<SessionPeseeServeur>> call, Throwable t) {
                traiterEchec(index, saisie, 0, null);
            }
        });
    }

    /** httpCode : code de la réponse, -1 = refus local définitif (voir SaisieLocale.seraRenvoyee). */
    private void markError(SaisieLocale saisie, String message, int httpCode) {
        if (saisie.getType() == SaisieType.PESEE_SESSION) {
            localDatabase.markErrorIfPayloadUnchanged(saisie.getLocalId(), message, saisie.getPayloadJson(), httpCode);
        } else {
            localDatabase.markError(saisie.getLocalId(), message, httpCode);
        }
    }

    /** Raison réelle du refus. Sur un 400 le corps est dans errorBody() (response.body()
     * est alors null) : le serveur y explique pourquoi (ex: "dépasserait l'effectif
     * vivant"), message qui n'était jamais affiché, seulement "Échec de l'envoi". */
    private String extractServerMessage(Response<? extends ApiEnvelope<?>> response) {
        try {
            if (response.body() != null && response.body().getMessage() != null) {
                return response.body().getMessage();
            }
            if (response.errorBody() != null) {
                // Corps parfois hors enveloppe ApiResponse (400 par défaut de Spring pour un
                // corps absent/illisible) : errors/message alors absents ou vides.
                ApiEnvelope<?> envelope = gson.fromJson(response.errorBody().string(), ApiEnvelope.class);
                if (envelope != null) {
                    if (envelope.getErrors() != null && !envelope.getErrors().isEmpty()) {
                        String premier = envelope.getErrors().get(0);
                        if (premier != null && !premier.trim().isEmpty()) return premier;
                    }
                    if (envelope.getMessage() != null && !envelope.getMessage().trim().isEmpty()) return envelope.getMessage();
                }
            }
        } catch (Exception ignored) {
            // corps illisible : message générique ci-dessous
        }
        if (response.code() == 401) return "Session expirée : reconnectez-vous en scannant votre QR code";
        if (response.code() == 403) return "Accès refusé par le serveur pour ce compte";
        if (response.code() >= 500) return "Serveur indisponible, réessayez plus tard";
        return "Échec de l'envoi";
    }

    /** true si la saisie a été mise en file d'envoi (le callback sera appelé), false si ce type n'est pas géré. */
    private boolean dispatch(SaisieLocale saisie, Callback<ApiEnvelope<CreatedEntityResponse>> callback) {
        String json = saisie.getPayloadJson();
        // Clé stable de la saisie (voir SaisieLocale.cleEnvoi) ; repli sur localId, lui aussi
        // fixe, pour une ligne qui n'en aurait pas.
        String cle = saisie.getCleEnvoi() != null ? saisie.getCleEnvoi() : saisie.getLocalId();

        switch (saisie.getType()) {
            case SOINS:
                // Un seul endpoint côté back pour Médicament/Autre/Vaccination — le
                // payload local porte déjà le bon "type" (VACCINATION/MEDICAMENT/AUTRE),
                // voir SaisieFormActivity.onValider.
                api.createSoins(cle, gson.fromJson(json, SoinsCreateRequest.class)).enqueue(callback);
                break;
            case ENTRETIEN:
                api.createEntretien(cle, gson.fromJson(json, com.mobile.diafarms.network.dto.EntretienCreateRequest.class)).enqueue(callback);
                break;
            case MORTALITE:
                api.createMortalite(cle, gson.fromJson(json, MortaliteCreateRequest.class)).enqueue(callback);
                break;
            case REFORME:
                api.createReforme(cle, gson.fromJson(json, ReformeCreateRequest.class)).enqueue(callback);
                break;
            case COLLECTE_OEUFS:
                api.createCollecteOeufs(cle, gson.fromJson(json, CollecteOeufsCreateRequest.class)).enqueue(callback);
                break;
            case ALIMENTATION_ACHAT:
                api.createAlimentationAchat(cle, saisie.getProjetUniqueId(), gson.fromJson(json, AlimentationCreateRequest.class))
                        .enqueue(callback);
                break;
            case ALIMENTATION_CONSOMMATION:
                api.createConsommationAliment(cle, gson.fromJson(json, ConsommationAlimentCreateRequest.class)).enqueue(callback);
                break;
            case VENTE_OEUFS:
                api.createVenteOeufs(cle, gson.fromJson(json, VenteOeufsCreateRequest.class)).enqueue(callback);
                break;
            case VENTE_REFORME:
                api.createVenteReforme(cle, gson.fromJson(json, VenteReformeCreateRequest.class)).enqueue(callback);
                break;
            case TRANSACTION_ENTREE:
            case TRANSACTION_SORTIE:
            // Vente de fientes = simple transaction "entrée" commune, catégorie fixe (voir
            // SaisieType.VENTE_FIENTES) : même endpoint et même payload que les deux ci-dessus.
            case VENTE_FIENTES:
                api.createTransaction(cle, gson.fromJson(json, TransactionCreateRequest.class)).enqueue(callback);
                break;
            case CLIENT_CREATE:
                api.createClient(cle, gson.fromJson(json, ClientCreateRequest.class)).enqueue(callback);
                break;
            case COMMANDE_CREATE:
                api.createCommande(cle, gson.fromJson(json, CommandeCreateRequest.class)).enqueue(callback);
                break;
            case SALAIRE_PAYER:
                api.payerSalaire(cle, gson.fromJson(json, SalairePayerRequest.class)).enqueue(callback);
                break;
            default:
                return false;
        }
        return true;
    }
}
