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
    /** LOCAL SEULEMENT : dernière version de la session connue du serveur. */
    public Long versionServeur;
    /** LOCAL SEULEMENT : uniqueId du dernier événement web déjà signalé sur ce téléphone
     * (les suivants sont « nouveaux » : notification). */
    public String dernierEvenementVu;
    /** LOCAL SEULEMENT : journal des modifications faites sur le web (affiché dans l'écran). */
    public List<EvenementWeb> evenementsWeb;
    /** LOCAL SEULEMENT : uniqueId des pesées supprimées sur le téléphone alors qu'elles
     * n'étaient pas marquées envoyées. Si le serveur les connaît quand même (réponse d'un
     * envoi précédent perdue), elles reviennent annulées, pour que l'annulation parte.
     * Oubliées dès que le serveur les montre annulées (ou que la session y est terminée). */
    public List<String> peseesSupprimees;

    public static class EvenementWeb {
        public String uniqueId;
        public String type;
        public String peseeUniqueId;
        public String description;
        public String parNom;
        public String date;
    }

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
        /** LOCAL SEULEMENT : pesée refusée par le serveur parce que la session y était déjà
         * terminée (sur le web). Gardée visible, hors totaux, jamais renvoyée. */
        public Boolean refusee;
        /** LOCAL SEULEMENT : "WEB" si ajoutée depuis le web (copie du serveur). */
        public String origine;
        /** LOCAL SEULEMENT : true si corrigée depuis le web (copie du serveur). */
        public Boolean modifiee;

        public boolean isAnnulee() { return Boolean.TRUE.equals(annulee); }
        public boolean isRefusee() { return Boolean.TRUE.equals(refusee); }
        public boolean isWeb() { return "WEB".equals(origine); }
        public boolean isModifiee() { return Boolean.TRUE.equals(modifiee); }
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
        r.versionServeur = null;
        r.dernierEvenementVu = null;
        r.evenementsWeb = null;
        r.peseesSupprimees = null;
        if (r.pesees != null) {
            List<Pesee> aEnvoyer = new ArrayList<>();
            for (Pesee p : r.pesees) {
                if (p == null || p.isRefusee()) continue; // déjà refusée : jamais renvoyée
                p.envoyee = null;
                p.origine = null;
                p.modifiee = null;
                p.refusee = null;
                aEnvoyer.add(p);
            }
            r.pesees = aEnvoyer;
        }
        return r;
    }

    /** Résultat de {@link #fusionner}. */
    public static class Fusion {
        /** Nouvel état local à enregistrer. */
        public SessionPeseeSyncRequest etat;
        /** true si {@code etat} ne contient plus rien à envoyer (ligne SYNCED), false si des
         * changements locaux restent en attente (ligne LOCAL). */
        public boolean aJour;
        /** Événements web jamais signalés sur ce téléphone (à notifier), chronologiques. */
        public List<EvenementWeb> nouveauxEvenements = new ArrayList<>();
        /** Nombre de pesées locales qui viennent d'être marquées refusées (session terminée
         * sur le serveur). */
        public int nouvellesRefusees;
        /** true si la session vient de passer TERMINEE du fait du serveur (web). */
        public boolean termineeParServeur;
        /** Lecture (sans envoi) plus ancienne que l'état local déjà connu : ignorée, rien
         * à écrire ({@code etat} = copie de l'état actuel). */
        public boolean ignoree;
    }

    /**
     * LA fusion de l'état serveur dans l'état local, après un envoi réussi ou une simple
     * lecture du détail. Règle : pour tout ce que le serveur connaît, le serveur fait foi ;
     * ce qui est local et pas encore envoyé est conservé (reste en attente).
     *
     * @param envoye  instantané envoyé (réponse de synchro), ou null pour une simple lecture
     *                (GET détail) sans envoi.
     * @param actuel  état local ACTUEL (il a pu changer pendant l'envoi).
     * @param serveur état renvoyé par le serveur. null = réponse sans détail : on suppose
     *                que le serveur détient exactement {@code envoye} (ancien comportement).
     *
     * Étape 1 (envoi seulement) : ce qui a changé localement PENDANT l'envoi.
     * <ul>
     *   <li>pesée envoyée, inchangée → envoyée ;</li>
     *   <li>supprimée localement pendant l'envoi → remise, annulée (annulation en attente) ;</li>
     *   <li>modifiée localement pendant l'envoi → l'ancienne version reste (annulation en
     *       attente) et les nouvelles valeurs passent sur une NOUVELLE pesée non envoyée.</li>
     * </ul>
     * Étape 2 : superposition de l'état serveur.
     * <ul>
     *   <li>pesée connue du serveur : nombre, poids, date, origine, modifiée = serveur ;
     *       annulée si le serveur OU le téléphone (annulation en attente) l'a annulée ;</li>
     *   <li>pesée du serveur absente du téléphone (ajoutée sur le web) : ajoutée, envoyée,
     *       à sa place chronologique ;</li>
     *   <li>pesée locale non envoyée : gardée telle quelle (en attente) ;</li>
     *   <li>peseesRefusees : marquées refusées (visibles, hors totaux, jamais renvoyées) ;</li>
     *   <li>session TERMINEE sur le serveur : statut/dateFin du serveur, figée ; toute pesée
     *       locale inconnue du serveur est refusée (il refuserait tout) et les annulations
     *       locales en attente sont abandonnées : l'état local devient celui du serveur.</li>
     * </ul>
     * Les objets passés ne sont pas modifiés.
     */
    public static Fusion fusionner(SessionPeseeSyncRequest envoye, SessionPeseeSyncRequest actuel,
                                   SessionPeseeServeur serveur) {
        if (serveur == null) serveur = vueServeur(envoye != null ? envoye : actuel);
        SessionPeseeSyncRequest r = copie(actuel);
        if (r.pesees == null) r.pesees = new ArrayList<>();
        r.pesees.removeIf(Objects::isNull);
        Fusion f = new Fusion();
        // Lecture périmée (réponse d'un GET partie avant une synchro plus récente) : ignorée.
        if (envoye == null && serveur.version != null && r.versionServeur != null
                && serveur.version < r.versionServeur) {
            f.etat = r;
            f.ignoree = true;
            f.aJour = false;
            return f;
        }
        List<String> supprimees = r.peseesSupprimees != null ? new ArrayList<>(r.peseesSupprimees) : new ArrayList<>();

        // Étape 1 : changements locaux faits pendant l'envoi.
        if (envoye != null && envoye.pesees != null) {
            List<Pesee> envoyees = envoye.pesees;
            for (int i = 0; i < envoyees.size(); i++) {
                Pesee e = envoyees.get(i);
                if (e == null || e.uniqueId == null || e.isRefusee()) continue;
                int idx = indexOf(r.pesees, e.uniqueId);
                if (idx < 0) {
                    Pesee remise = copie(e);
                    remise.annulee = true;
                    remise.envoyee = true;
                    r.pesees.add(Math.min(i, r.pesees.size()), remise);
                    continue;
                }
                Pesee c = r.pesees.get(idx);
                if (!memesValeurs(c, e)) {
                    remplacerParNouvelle(r.pesees, idx, e);
                } else {
                    c.envoyee = true;
                    if (e.isAnnulee()) c.annulee = true; // le serveur ne dés-annule jamais
                }
            }
        }

        // Étape 2 : l'état serveur fait foi pour tout ce qu'il connaît.
        java.util.Map<String, SessionPeseeServeur.Pesee> surServeur = new java.util.LinkedHashMap<>();
        if (serveur.pesees != null) {
            for (SessionPeseeServeur.Pesee s : serveur.pesees) {
                if (s != null && s.uniqueId != null) surServeur.put(s.uniqueId, s);
            }
        }
        for (int i = 0; i < r.pesees.size(); i++) {
            Pesee c = r.pesees.get(i);
            SessionPeseeServeur.Pesee s = c.uniqueId != null ? surServeur.get(c.uniqueId) : null;
            if (s == null) continue;
            // Pesée crue non envoyée (réponse d'un envoi précédent perdue) mais modifiée
            // depuis sur le téléphone : le serveur a l'ancienne valeur et ignorerait la
            // nouvelle. Même traitement qu'une modification pendant l'envoi.
            boolean creueNonEnvoyee = Boolean.FALSE.equals(c.envoyee);
            if (creueNonEnvoyee && (!Objects.equals(c.nombreSujets, s.nombreSujets)
                    || !memePoids(c.poidsKg, s.poidsKg))) {
                Pesee ancienne = copie(c);
                ancienne.nombreSujets = s.nombreSujets;
                ancienne.poidsKg = s.poidsKg;
                remplacerParNouvelle(r.pesees, i, ancienne);
                c = r.pesees.get(i);
            }
            c.nombreSujets = s.nombreSujets;
            c.poidsKg = s.poidsKg;
            if (s.dateHeure != null) c.dateHeure = s.dateHeure;
            c.annulee = Boolean.TRUE.equals(s.annulee) || c.isAnnulee();
            c.envoyee = true;
            c.refusee = null;
            c.origine = s.origine;
            c.modifiee = Boolean.TRUE.equals(s.modifiee) ? Boolean.TRUE : null;
        }
        // Pesées du serveur inconnues du téléphone (web, autre appareil).
        for (SessionPeseeServeur.Pesee s : surServeur.values()) {
            if (indexOf(r.pesees, s.uniqueId) >= 0) continue;
            boolean supprimee = supprimees.contains(s.uniqueId);
            Pesee n = new Pesee();
            n.uniqueId = s.uniqueId;
            n.nombreSujets = s.nombreSujets;
            n.poidsKg = s.poidsKg;
            n.dateHeure = s.dateHeure;
            // Supprimée ici mais connue du serveur : revient annulée (annulation à envoyer).
            n.annulee = Boolean.TRUE.equals(s.annulee) || (supprimee && !serveur.isTerminee());
            n.envoyee = true;
            n.origine = s.origine;
            n.modifiee = Boolean.TRUE.equals(s.modifiee) ? Boolean.TRUE : null;
            r.pesees.add(positionChronologique(r.pesees, n.dateHeure), n);
        }
        // Refusées explicitement par le serveur.
        if (serveur.peseesRefusees != null) {
            for (String uid : serveur.peseesRefusees) {
                int idx = uid != null ? indexOf(r.pesees, uid) : -1;
                if (idx >= 0 && !surServeur.containsKey(uid)) f.nouvellesRefusees += refuser(r.pesees.get(idx));
            }
        }

        // Pierres tombales : oubliées quand le serveur montre la pesée annulée, ou quand la
        // session y est terminée (plus rien ne peut y être annulé).
        java.util.Iterator<String> it = supprimees.iterator();
        while (it.hasNext()) {
            SessionPeseeServeur.Pesee s = surServeur.get(it.next());
            if (serveur.isTerminee() || (s != null && Boolean.TRUE.equals(s.annulee))) it.remove();
        }
        r.peseesSupprimees = supprimees.isEmpty() ? null : supprimees;

        boolean etaitTerminee = r.isTerminee() && Boolean.TRUE.equals(r.termineeEnvoyee);
        if (serveur.isTerminee()) {
            f.termineeParServeur = !etaitTerminee && !(envoye != null && envoye.isTerminee());
            r.statut = STATUT_TERMINEE;
            if (serveur.dateFin != null) r.dateFin = serveur.dateFin;
            r.termineeEnvoyee = true;
            for (Pesee c : r.pesees) {
                SessionPeseeServeur.Pesee s = surServeur.get(c.uniqueId);
                if (s == null) {
                    if (!c.isRefusee() && !Boolean.TRUE.equals(c.envoyee)) f.nouvellesRefusees += refuser(c);
                } else {
                    c.annulee = Boolean.TRUE.equals(s.annulee); // annulations en attente abandonnées
                }
            }
        } else if (r.isTerminee()) {
            // Clôture locale pas encore reçue par le serveur. Une simple lecture ne revient
            // jamais sur une clôture déjà acceptée (true → false).
            if (envoye != null || !Boolean.TRUE.equals(r.termineeEnvoyee)) r.termineeEnvoyee = false;
            if (!Boolean.TRUE.equals(r.termineeEnvoyee)) {
                // Des pesées (web) ont pu arriver après la clôture locale : dateFin ≥ la
                // dernière pesée active (le serveur refuse dateFin < dateDebut, et une fin
                // avant la dernière pesée serait incohérente).
                for (Pesee c : r.peseesActives()) {
                    if (c.dateHeure != null && (r.dateFin == null || c.dateHeure.compareTo(r.dateFin) > 0)) {
                        r.dateFin = c.dateHeure;
                    }
                }
            }
        }
        if (serveur.nombreParDefaut != null) r.nombreParDefaut = serveur.nombreParDefaut;
        if (serveur.dateDebut != null) r.dateDebut = serveur.dateDebut;
        if (serveur.version != null) r.versionServeur = serveur.version;

        // Journal web : tout est gardé pour l'affichage, seuls les événements postérieurs
        // au dernier déjà signalé sont « nouveaux ».
        if (serveur.evenements != null && !serveur.evenements.isEmpty()) {
            List<EvenementWeb> journal = new ArrayList<>();
            for (SessionPeseeServeur.Evenement e : serveur.evenements) {
                if (e == null) continue;
                EvenementWeb w = new EvenementWeb();
                w.uniqueId = e.uniqueId;
                w.type = e.type;
                w.peseeUniqueId = e.peseeUniqueId;
                w.description = e.description;
                w.parNom = e.parNom;
                w.date = e.date;
                journal.add(w);
            }
            int dejaVus = 0;
            if (r.dernierEvenementVu != null) {
                for (int i = 0; i < journal.size(); i++) {
                    if (r.dernierEvenementVu.equals(journal.get(i).uniqueId)) dejaVus = i + 1;
                }
            }
            f.nouveauxEvenements.addAll(journal.subList(dejaVus, journal.size()));
            r.evenementsWeb = journal;
            if (!journal.isEmpty() && journal.get(journal.size() - 1).uniqueId != null) {
                r.dernierEvenementVu = journal.get(journal.size() - 1).uniqueId;
            }
        }

        f.etat = r;
        f.aJour = !resteAEnvoyer(r, surServeur, serveur.isTerminee());
        return f;
    }

    /** Session importée depuis le serveur (créée sur le web ou sur un autre téléphone) :
     * tout est envoyé, le journal existant est considéré comme déjà vu (pas de notification). */
    public static SessionPeseeSyncRequest depuisServeur(SessionPeseeServeur serveur) {
        SessionPeseeSyncRequest vide = new SessionPeseeSyncRequest();
        vide.uniqueId = serveur.uniqueId;
        vide.projetUniqueId = serveur.projetUniqueId;
        vide.nombreParDefaut = serveur.nombreParDefaut;
        vide.dateDebut = serveur.dateDebut;
        vide.statut = STATUT_EN_COURS;
        SessionPeseeSyncRequest r = fusionner(null, vide, serveur).etat;
        if (r.evenementsWeb != null && !r.evenementsWeb.isEmpty()) {
            r.dernierEvenementVu = r.evenementsWeb.get(r.evenementsWeb.size() - 1).uniqueId;
        }
        if (r.isTerminee()) r.termineeEnvoyee = true;
        return r;
    }

    /** Quelque chose reste-t-il à envoyer après la fusion ? */
    private static boolean resteAEnvoyer(SessionPeseeSyncRequest r,
                                         java.util.Map<String, SessionPeseeServeur.Pesee> surServeur,
                                         boolean termineeServeur) {
        if (termineeServeur) return false; // le serveur refuserait tout : rien à renvoyer
        if (r.isTerminee()) return !Boolean.TRUE.equals(r.termineeEnvoyee); // clôture locale en attente
        for (Pesee c : r.pesees) {
            if (c.isRefusee()) continue;
            SessionPeseeServeur.Pesee s = surServeur.get(c.uniqueId);
            if (s == null) {
                if (!Boolean.TRUE.equals(c.envoyee)) return true; // nouvelle pesée en attente
            } else if (c.isAnnulee() && !Boolean.TRUE.equals(s.annulee)) {
                return true; // annulation en attente
            }
        }
        return false;
    }

    /** L'ancienne version (envoyée) reste, avec une annulation en attente ; les valeurs
     * locales passent sur une nouvelle pesée non envoyée, placée juste après. */
    private static void remplacerParNouvelle(List<Pesee> pesees, int idx, Pesee ancienneEnvoyee) {
        Pesee locale = pesees.get(idx);
        Pesee nouvelle = copie(locale);
        nouvelle.uniqueId = UUID.randomUUID().toString();
        nouvelle.envoyee = false;
        nouvelle.origine = null;
        nouvelle.modifiee = null;
        Pesee neutralisee = copie(ancienneEnvoyee);
        neutralisee.annulee = true;
        neutralisee.envoyee = true;
        pesees.set(idx, neutralisee);
        pesees.add(idx + 1, nouvelle);
    }

    /** Retourne 1 si la pesée compte pour l'utilisateur (non annulée ici), 0 sinon. */
    private static int refuser(Pesee p) {
        if (p.isRefusee()) return 0;
        p.refusee = true;
        p.envoyee = true; // plus rien à envoyer pour elle
        return p.isAnnulee() ? 0 : 1;
    }

    private static boolean memesValeurs(Pesee a, Pesee b) {
        return Objects.equals(a.nombreSujets, b.nombreSujets) && Objects.equals(a.poidsKg, b.poidsKg);
    }

    private static boolean memePoids(Double a, Double b) {
        if (a == null || b == null) return a == b;
        return Math.abs(a - b) < 0.0005;
    }

    /** Index d'insertion : avant la première pesée plus récente (dates ISO comparables). */
    private static int positionChronologique(List<Pesee> pesees, String dateHeure) {
        if (dateHeure == null) return pesees.size();
        for (int i = 0; i < pesees.size(); i++) {
            String d = pesees.get(i).dateHeure;
            if (d != null && d.compareTo(dateHeure) > 0) return i;
        }
        return pesees.size();
    }

    /** Repli quand la réponse ne porte pas le détail : le serveur détient ce qui a été envoyé. */
    private static SessionPeseeServeur vueServeur(SessionPeseeSyncRequest envoye) {
        SessionPeseeServeur s = new SessionPeseeServeur();
        s.uniqueId = envoye.uniqueId;
        s.statut = envoye.isTerminee() ? STATUT_TERMINEE : STATUT_EN_COURS;
        s.dateFin = envoye.dateFin;
        s.pesees = new ArrayList<>();
        if (envoye.pesees != null) {
            for (Pesee p : envoye.pesees) {
                if (p == null || p.uniqueId == null || p.isRefusee()) continue;
                SessionPeseeServeur.Pesee sp = new SessionPeseeServeur.Pesee();
                sp.uniqueId = p.uniqueId;
                sp.nombreSujets = p.nombreSujets;
                sp.poidsKg = p.poidsKg;
                sp.dateHeure = p.dateHeure;
                sp.annulee = p.isAnnulee();
                sp.origine = p.origine;
                sp.modifiee = p.modifiee;
                s.pesees.add(sp);
            }
        }
        s.evenements = null;
        return s;
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
            for (Pesee p : pesees) if (p != null && !p.isAnnulee() && !p.isRefusee()) actives.add(p);
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
