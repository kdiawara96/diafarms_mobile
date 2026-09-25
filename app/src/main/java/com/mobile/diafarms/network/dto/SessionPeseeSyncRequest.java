package com.mobile.diafarms.network.dto;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Miroir de SessionPeseeSyncRequest côté backend (POST pesees/sessions/sync) : l'état
 * COMPLET d'une session de pesée (session + toutes ses pesées, annulées comprises).
 * Stocké tel quel dans saisies_locales.payload_json (une seule ligne locale par
 * session, voir SaisieType.PESEE_SESSION) et renvoyé en entier à chaque
 * synchronisation : le serveur est idempotent (uniqueId générés sur le téléphone),
 * renvoyer le même instantané ne crée jamais de doublon.
 */
public class SessionPeseeSyncRequest {
    public static final String STATUT_EN_COURS = "EN_COURS";
    public static final String STATUT_TERMINEE = "TERMINEE";

    public String uniqueId;
    public String projetUniqueId;
    public Integer nombreParDefaut;
    public String dateDebut;   // "yyyy-MM-dd'T'HH:mm:ss" (heure locale)
    public String statut;      // EN_COURS | TERMINEE
    public String dateFin;     // null tant que EN_COURS
    public List<Pesee> pesees = new ArrayList<>();
    /** LOCAL SEULEMENT (retiré avant l'envoi, voir pourEnvoi) : true quand l'état TERMINEE
     * a été accepté par le serveur. La session est alors figée (le serveur la refuse
     * désormais) ; avant, elle peut être rouverte sur le téléphone. */
    public Boolean termineeEnvoyee;

    public static class Pesee {
        public String uniqueId;
        public Integer nombreSujets;
        public Double poidsKg;
        public String dateHeure; // "yyyy-MM-dd'T'HH:mm:ss"
        public Boolean annulee;
        /** LOCAL SEULEMENT (retiré avant l'envoi) : true quand le serveur a reçu cette
         * pesée. Le serveur ignore ensuite toute modification de son nombre/poids : elle
         * ne peut plus qu'être annulée. Tant qu'elle n'est pas envoyée, on la modifie ou
         * la supprime librement sur le téléphone. null = donnée antérieure à la 1.27. */
        public Boolean envoyee;

        public boolean isAnnulee() { return Boolean.TRUE.equals(annulee); }
    }

    public boolean isTerminee() { return STATUT_TERMINEE.equals(statut); }

    /**
     * La pesée est-elle connue du serveur ? {@code sessionDejaRecue} (server_unique_id de
     * la ligne locale non null) ne sert que pour les pesées enregistrées avant la 1.27
     * (sans indicateur) : par prudence, elles sont alors considérées comme envoyées.
     */
    public static boolean estEnvoyee(Pesee p, boolean sessionDejaRecue) {
        return p.envoyee != null ? p.envoyee : sessionDejaRecue;
    }

    /** true si la clôture a déjà été acceptée par le serveur (session figée).
     * {@code ligneSynchronisee} : repli pour les sessions antérieures à la 1.27. */
    public boolean isClotureEnvoyee(boolean ligneSynchronisee) {
        if (!isTerminee()) return false;
        return termineeEnvoyee != null ? termineeEnvoyee : ligneSynchronisee;
    }

    /** Copie à envoyer au serveur : sans les indicateurs locaux (null → absents du JSON). */
    public SessionPeseeSyncRequest pourEnvoi() {
        SessionPeseeSyncRequest r = copie(this);
        r.termineeEnvoyee = null;
        if (r.pesees != null) for (Pesee p : r.pesees) p.envoyee = null;
        return r;
    }

    /**
     * Nouvel état local après un envoi RÉUSSI (2xx) de l'instantané {@code envoye}, alors
     * que l'état local a pu changer pendant l'envoi ({@code actuel}). Le serveur détient
     * maintenant {@code envoye} ; il n'accepte ensuite, pour une pesée existante, que
     * l'annulation. Donc, pour chaque pesée envoyée :
     * <ul>
     *   <li>inchangée localement → marquée envoyée ;</li>
     *   <li>supprimée localement pendant l'envoi → remise dans la liste, annulée et
     *       envoyée (l'annulation part au prochain envoi) ;</li>
     *   <li>modifiée localement pendant l'envoi (nombre ou poids) → l'ancienne version
     *       reste, annulée et envoyée, et les nouvelles valeurs passent sur une NOUVELLE
     *       pesée (nouvel uniqueId, non envoyée) juste après.</li>
     * </ul>
     * Si l'état envoyé était TERMINEE, le serveur a figé la session : les changements
     * locaux faits pendant l'envoi (réouverture, pesées) ne pourraient jamais être
     * acceptés, on revient donc exactement à l'état envoyé.
     * Les pesées ajoutées pendant l'envoi restent non envoyées. Les objets passés ne sont
     * pas modifiés. {@code apresEnvoiReussi(envoye, envoye)} = ce que détient le serveur.
     */
    public static SessionPeseeSyncRequest apresEnvoiReussi(SessionPeseeSyncRequest envoye,
                                                           SessionPeseeSyncRequest actuel) {
        SessionPeseeSyncRequest r = copie(envoye.isTerminee() ? envoye : actuel);
        if (r.pesees == null) r.pesees = new ArrayList<>();
        if (envoye.isTerminee()) {
            r.termineeEnvoyee = true;
            for (Pesee p : r.pesees) p.envoyee = true;
            return r;
        }
        List<Pesee> envoyees = envoye.pesees != null ? envoye.pesees : new ArrayList<>();
        for (int i = 0; i < envoyees.size(); i++) {
            Pesee e = envoyees.get(i);
            if (e == null || e.uniqueId == null) continue;
            int idx = indexOf(r.pesees, e.uniqueId);
            if (idx < 0) {
                Pesee neutralisee = copie(e);
                neutralisee.annulee = true;
                neutralisee.envoyee = true;
                r.pesees.add(Math.min(i, r.pesees.size()), neutralisee);
                continue;
            }
            Pesee c = r.pesees.get(idx);
            boolean modifiee = !Objects.equals(c.nombreSujets, e.nombreSujets)
                    || !Objects.equals(c.poidsKg, e.poidsKg);
            if (modifiee) {
                Pesee nouvelle = copie(c);
                nouvelle.uniqueId = UUID.randomUUID().toString();
                nouvelle.envoyee = false;
                Pesee neutralisee = copie(e);
                neutralisee.annulee = true;
                neutralisee.envoyee = true;
                r.pesees.set(idx, neutralisee);
                r.pesees.add(idx + 1, nouvelle);
            } else {
                c.envoyee = true;
                if (e.isAnnulee()) c.annulee = true; // le serveur ne dés-annule jamais
            }
        }
        return r;
    }

    private static int indexOf(List<Pesee> pesees, String uniqueId) {
        for (int i = 0; i < pesees.size(); i++) {
            if (pesees.get(i) != null && uniqueId.equals(pesees.get(i).uniqueId)) return i;
        }
        return -1;
    }

    private static final Gson GSON = new Gson();

    private static <T> T copie(T o) {
        @SuppressWarnings("unchecked") Class<T> cls = (Class<T>) o.getClass();
        return GSON.fromJson(GSON.toJson(o), cls);
    }

    /** Pesées prises en compte dans les totaux (non annulées). */
    public List<Pesee> peseesActives() {
        List<Pesee> actives = new ArrayList<>();
        if (pesees != null) {
            for (Pesee p : pesees) if (!p.isAnnulee()) actives.add(p);
        }
        return actives;
    }

    public int totalSujets() {
        int total = 0;
        for (Pesee p : peseesActives()) total += p.nombreSujets != null ? p.nombreSujets : 0;
        return total;
    }

    public double poidsTotalKg() {
        double total = 0;
        for (Pesee p : peseesActives()) total += p.poidsKg != null ? p.poidsKg : 0;
        return total;
    }

    /** Poids total / total sujets (pesées non annulées), 0 si aucun sujet. */
    public double poidsMoyenKg() {
        int sujets = totalSujets();
        return sujets > 0 ? poidsTotalKg() / sujets : 0;
    }
}
