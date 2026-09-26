package com.mobile.diafarms.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.mobile.diafarms.R;
import com.mobile.diafarms.data.CachePrefetcher;
import com.mobile.diafarms.data.CommandesHorsLigne;
import com.mobile.diafarms.data.LocalDatabase;
import com.mobile.diafarms.data.SessionManager;
import com.mobile.diafarms.models.SaisieType;
import com.mobile.diafarms.models.User;
import com.mobile.diafarms.network.dto.CommandeResponse;
import com.mobile.diafarms.ui.saisie.SaisieFormActivity;
import com.mobile.diafarms.util.AlveoleUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Commandes ouvertes de la ferme (VENTE, RESPONSABLE, ADMIN ; COMPTABLE pour encaisser) :
 * dernière liste connue du serveur (cache, donc consultable hors ligne), actualisée dès
 * que le réseau répond. Le reste à livrer tient compte des livraisons saisies ici et pas
 * encore envoyées. Un appui ouvre le détail, d'où partent « Livrer » et « Encaisser ».
 */
public class CommandesActivity extends AppCompatActivity {

    private LocalDatabase localDatabase;
    private User currentUser;
    private ListView listCommandes;
    private TextView tvEmpty, tvInfo;
    private Button btnActualiser;
    private final List<CommandeResponse> commandes = new ArrayList<>();
    private CommandeAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_commandes);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_commandes), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        localDatabase = new LocalDatabase(this).figee();
        currentUser = new SessionManager(this).getCurrentUser();
        if (currentUser == null) {
            finish();
            return;
        }

        findViewById(R.id.btnBackCommandes).setOnClickListener(v -> finish());
        listCommandes = findViewById(R.id.listCommandes);
        tvEmpty = findViewById(R.id.tvEmptyCommandes);
        tvInfo = findViewById(R.id.tvCommandesInfo);
        btnActualiser = findViewById(R.id.btnActualiserCommandes);
        btnActualiser.setOnClickListener(v -> actualiser(true));

        adapter = new CommandeAdapter();
        listCommandes.setAdapter(adapter);
        listCommandes.setOnItemClickListener((parent, view, position, id) -> ouvrirDetail(commandes.get(position)));

        afficher();
        actualiser(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (adapter != null) afficher(); // livraison saisie entre-temps : reste à jour
    }

    private void actualiser(boolean manuel) {
        btnActualiser.setEnabled(false);
        CachePrefetcher.rafraichirCommandes(this, localDatabase, ok -> {
            if (isFinishing() || isDestroyed()) return;
            btnActualiser.setEnabled(true);
            if (ok) afficher();
            else if (manuel) Toast.makeText(this, "Hors ligne : dernière liste connue affichée", Toast.LENGTH_SHORT).show();
        });
    }

    private void afficher() {
        commandes.clear();
        commandes.addAll(CommandesHorsLigne.lire(localDatabase));
        // Livraison prévue la plus proche d'abord, sans date à la fin.
        Collections.sort(commandes, (a, b) -> {
            String da = a.dateLivraisonPrevue != null ? a.dateLivraisonPrevue : "9999";
            String db = b.dateLivraisonPrevue != null ? b.dateLivraisonPrevue : "9999";
            return da.compareTo(db);
        });
        adapter.notifyDataSetChanged();
        boolean vide = commandes.isEmpty();
        tvEmpty.setVisibility(vide ? View.VISIBLE : View.GONE);
        listCommandes.setVisibility(vide ? View.GONE : View.VISIBLE);
        long maj = localDatabase.getCacheUpdatedAt(CachePrefetcher.CACHE_COMMANDES_OUVERTES);
        tvInfo.setText(maj > 0
                ? "Données du " + new SimpleDateFormat("dd/MM à HH:mm", Locale.FRANCE).format(new Date(maj))
                : "Liste jamais chargée : connectez-vous au réseau puis actualisez.");
    }

    /** Reste à livrer vu du téléphone : dernier reste connu du serveur moins les
     * livraisons saisies ici et pas encore envoyées. */
    private int resteLocal(CommandeResponse c) {
        return c.resteServeur() - CommandesHorsLigne.quantiteEnAttente(localDatabase, c.uniqueId, null);
    }

    static String quantite(CommandeResponse c, int n) {
        return c.estOeufs() ? AlveoleUtils.formatOeufsAvecAlveoles(n) : n + (n > 1 ? " sujets" : " sujet");
    }

    static String produit(CommandeResponse c) {
        if (c.estOeufs()) return "Œufs";
        return c.estAuKilo() ? "Réforme au kilo" : "Réforme par tête";
    }

    private static String montant(Double v) {
        return String.format(Locale.FRANCE, "%,.0f F", v != null ? v : 0.0);
    }

    private static String dateCourte(String iso) {
        if (iso == null || iso.length() < 10) return null;
        return iso.substring(8, 10) + "/" + iso.substring(5, 7) + "/" + iso.substring(0, 4);
    }

    private void ouvrirDetail(CommandeResponse c) {
        int enAttente = CommandesHorsLigne.quantiteEnAttente(localDatabase, c.uniqueId, null);
        int reste = c.resteServeur() - enAttente;
        StringBuilder m = new StringBuilder();
        m.append("Client : ").append(c.clientNom).append('\n');
        m.append("Produit : ").append(produit(c)).append('\n');
        if (c.magasinNom != null) m.append("Magasin : ").append(c.magasinNom).append('\n');
        m.append("Commandé : ").append(quantite(c, c.quantite != null ? c.quantite : 0)).append('\n');
        m.append("Livré : ").append(quantite(c, c.quantiteLivree != null ? c.quantiteLivree : 0));
        if (c.estAuKilo() && c.poidsLivreKg != null && c.poidsLivreKg > 0) {
            m.append(String.format(Locale.FRANCE, " (%s kg)", formatKg(c.poidsLivreKg)));
        }
        m.append('\n');
        m.append("Reste à livrer : ").append(quantite(c, Math.max(0, reste)));
        if (enAttente > 0) m.append(" (dont ").append(quantite(c, enAttente)).append(" livrés sur ce téléphone, pas encore envoyés)");
        m.append("\n\n");
        if (c.estAuKilo()) {
            m.append("Tarification : au kilo, ").append(montant(c.prixKgEstime)).append("/kg estimé\n");
        } else {
            Double pu = c.prixUnitaireLivraison();
            m.append("Tarification : par ").append(c.estOeufs() ? "œuf" : "tête");
            if (pu != null) m.append(", ").append(String.format(Locale.FRANCE, "%,.0f F", pu));
            m.append('\n');
        }
        m.append("Montant estimé : ").append(montant(c.montantEstime)).append('\n');
        m.append("Acompte reçu : ").append(montant(c.acompteRecu)).append('\n');
        m.append("Acompte utilisé (livraisons) : ").append(montant(c.acompteImpute)).append('\n');
        m.append("Acompte réservé à la commande : ").append(montant(c.acompteReserve)).append('\n');
        if (c.resteAPayerLivre != null && c.resteAPayerLivre > 0) {
            m.append("Reste à payer sur le livré : ").append(montant(c.resteAPayerLivre)).append('\n');
        }
        String prevue = dateCourte(c.dateLivraisonPrevue);
        if (prevue != null) m.append("Livraison prévue : ").append(prevue).append('\n');
        if (c.statutLibelle != null) m.append("Statut : ").append(c.statutLibelle);

        boolean demo = currentUser.isConsultationSeule();
        MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(this)
                .setTitle("Commande de " + c.clientNom)
                .setMessage(m.toString())
                .setNegativeButton("Fermer", null);
        if (!demo && currentUser.peutGererCommandes() && reste > 0) {
            b.setPositiveButton("Livrer", (d, w) -> ouvrirSaisie(SaisieType.LIVRAISON_COMMANDE, c));
        }
        ajouterActionEncaisser(b, c, demo);
        b.show();
    }

    /** Encaisser un paiement réservé à cette commande (client et commande présélectionnés). */
    private void ajouterActionEncaisser(MaterialAlertDialogBuilder b, CommandeResponse c, boolean demo) {
        if (demo || !currentUser.peutEncaisser()) return;
        b.setNeutralButton("Encaisser", (d, w) -> ouvrirSaisie(SaisieType.PAIEMENT_CLIENT, c));
    }

    private void ouvrirSaisie(SaisieType type, CommandeResponse c) {
        Intent intent = new Intent(this, SaisieFormActivity.class);
        intent.putExtra(SaisieFormActivity.EXTRA_TYPE, type.name());
        intent.putExtra(SaisieFormActivity.EXTRA_COMMANDE_ID, c.uniqueId);
        startActivity(intent);
    }

    static String formatKg(double kg) {
        java.text.NumberFormat nf = java.text.NumberFormat.getNumberInstance(Locale.FRANCE);
        nf.setMaximumFractionDigits(3);
        return nf.format(kg);
    }

    private class CommandeAdapter extends BaseAdapter {
        @Override public int getCount() { return commandes.size(); }
        @Override public CommandeResponse getItem(int position) { return commandes.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView != null ? convertView
                    : LayoutInflater.from(CommandesActivity.this).inflate(R.layout.item_commande, parent, false);
            CommandeResponse c = commandes.get(position);
            ((TextView) view.findViewById(R.id.tvCommandeTitre)).setText(c.clientNom + " · " + produit(c));
            int reste = resteLocal(c);
            int enAttente = c.resteServeur() - reste;
            ((TextView) view.findViewById(R.id.tvCommandeReste)).setText("Reste à livrer : " + quantite(c, Math.max(0, reste))
                    + (enAttente > 0 ? " (livraison en attente d'envoi)" : ""));
            StringBuilder meta = new StringBuilder(c.statutLibelle != null ? c.statutLibelle : "");
            String prevue = dateCourte(c.dateLivraisonPrevue);
            if (prevue != null) meta.append(meta.length() > 0 ? " · " : "").append("prévue le ").append(prevue);
            if (c.acompteReserve != null && c.acompteReserve > 0) {
                meta.append(meta.length() > 0 ? " · " : "").append("acompte réservé ").append(montant(c.acompteReserve));
            }
            ((TextView) view.findViewById(R.id.tvCommandeMeta)).setText(meta.toString());
            return view;
        }
    }
}
