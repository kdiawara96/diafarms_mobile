package com.mobile.diafarms.activity;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.mobile.diafarms.R;
import com.mobile.diafarms.data.LocalDatabase;
import com.mobile.diafarms.models.SaisieLocale;
import com.mobile.diafarms.models.SaisieType;
import com.mobile.diafarms.ui.saisie.SaisieFormActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Liste toutes les saisies enregistrées sur l'appareil (production comme finance),
 * filtrable par type. Les saisies encore LOCAL ou en ERROR (pas encore synchronisées
 * avec succès) sont modifiables et supprimables ; les saisies SYNCED sont en lecture seule.
 */
public class MesSaisiesActivity extends AppCompatActivity {

    private LocalDatabase localDatabase;
    private AutoCompleteTextView spinnerFiltreType;
    private ListView listSaisies;
    private TextView tvEmpty;
    private SaisieAdapter adapter;
    private SaisieType filtreActuel; // null = toutes

    private final ActivityResultLauncher<Intent> editLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> refreshList());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_mes_saisies);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_mes_saisies), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        localDatabase = new LocalDatabase(this);

        findViewById(R.id.btnBackMesSaisies).setOnClickListener(v -> finish());

        spinnerFiltreType = findViewById(R.id.spinnerFiltreType);
        listSaisies = findViewById(R.id.listSaisies);
        tvEmpty = findViewById(R.id.tvEmptyMesSaisies);

        setupFiltre();

        adapter = new SaisieAdapter(new ArrayList<>());
        listSaisies.setAdapter(adapter);

        refreshList();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshList();
    }

    private void setupFiltre() {
        List<String> labels = new ArrayList<>();
        labels.add("Toutes les saisies");
        for (SaisieType t : SaisieType.values()) {
            labels.add(t.getLabel());
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, labels);
        spinnerFiltreType.setAdapter(adapter);
        spinnerFiltreType.setText(labels.get(0), false);

        spinnerFiltreType.setOnItemClickListener((parent, view, position, id) -> {
            filtreActuel = position == 0 ? null : SaisieType.values()[position - 1];
            refreshList();
        });
    }

    private void refreshList() {
        List<SaisieLocale> saisies = filtreActuel == null
                ? localDatabase.getAllSaisies()
                : localDatabase.getSaisiesByType(filtreActuel);

        adapter.setItems(saisies);
        tvEmpty.setVisibility(saisies.isEmpty() ? View.VISIBLE : View.GONE);
        listSaisies.setVisibility(saisies.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void openEdit(SaisieLocale saisie) {
        Intent intent = new Intent(this, SaisieFormActivity.class);
        intent.putExtra(SaisieFormActivity.EXTRA_TYPE, saisie.getType().name());
        intent.putExtra(SaisieFormActivity.EXTRA_PROJET_ID, saisie.getProjetUniqueId());
        intent.putExtra(SaisieFormActivity.EXTRA_PROJET_LABEL, saisie.getProjetLabel());
        intent.putExtra(SaisieFormActivity.EXTRA_LOCAL_ID, saisie.getLocalId());
        editLauncher.launch(intent);
    }

    private void confirmDelete(SaisieLocale saisie) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Supprimer cette saisie")
                .setMessage("Cette saisie locale sera définitivement supprimée. Continuer ?")
                .setPositiveButton("Supprimer", (dialog, which) -> {
                    localDatabase.deleteSaisie(saisie.getLocalId());
                    Toast.makeText(this, "Saisie supprimée", Toast.LENGTH_SHORT).show();
                    refreshList();
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    private class SaisieAdapter extends BaseAdapter {
        private List<SaisieLocale> items;
        private final SimpleDateFormat displayFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE);

        SaisieAdapter(List<SaisieLocale> items) {
            this.items = items;
        }

        void setItems(List<SaisieLocale> items) {
            this.items = items;
            notifyDataSetChanged();
        }

        @Override public int getCount() { return items.size(); }
        @Override public SaisieLocale getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView != null ? convertView
                    : LayoutInflater.from(MesSaisiesActivity.this).inflate(R.layout.item_saisie_locale, parent, false);

            SaisieLocale saisie = items.get(position);

            ImageView ivIcon = view.findViewById(R.id.ivItemIcon);
            TextView tvType = view.findViewById(R.id.tvItemType);
            TextView tvSummary = view.findViewById(R.id.tvItemSummary);
            TextView tvMeta = view.findViewById(R.id.tvItemMeta);
            TextView tvStatus = view.findViewById(R.id.tvItemStatus);
            ImageButton btnEdit = view.findViewById(R.id.btnItemEdit);
            ImageButton btnDelete = view.findViewById(R.id.btnItemDelete);

            ivIcon.setImageResource(iconePour(saisie.getType()));
            tvType.setText(saisie.getType().getLabel());
            tvSummary.setText(saisie.getDisplaySummary());

            String projet = saisie.getProjetLabel() != null ? saisie.getProjetLabel() : "Commun";
            tvMeta.setText(projet + " · " + displayFormat.format(saisie.getCreatedAt()));

            switch (saisie.getSyncStatus()) {
                case SaisieLocale.STATUT_SYNCED:
                    tvStatus.setText("Synchronisé");
                    tvStatus.setTextColor(Color.WHITE);
                    tvStatus.setBackgroundColor(0xFF2E7D32);
                    break;
                case SaisieLocale.STATUT_ERROR:
                    tvStatus.setText("Erreur : " + (saisie.getErrorMessage() != null ? saisie.getErrorMessage() : "envoi échoué"));
                    tvStatus.setTextColor(Color.WHITE);
                    tvStatus.setBackgroundColor(0xFFC62828);
                    break;
                default:
                    tvStatus.setText("En attente de synchronisation");
                    tvStatus.setTextColor(Color.WHITE);
                    tvStatus.setBackgroundColor(0xFFEF6C00);
            }

            boolean editable = saisie.isEditable();
            btnEdit.setVisibility(editable ? View.VISIBLE : View.GONE);
            btnDelete.setVisibility(editable ? View.VISIBLE : View.GONE);
            btnEdit.setOnClickListener(v -> openEdit(saisie));
            btnDelete.setOnClickListener(v -> confirmDelete(saisie));

            return view;
        }

        // Mêmes icônes que les cartes de saisie de l'accueil (HomeActivity), pour rester
        // cohérent visuellement entre les deux écrans.
        private int iconePour(SaisieType type) {
            switch (type) {
                case COLLECTE_OEUFS:
                case VENTE_OEUFS:
                    return R.drawable.oeufs;
                case ALIMENTATION_ACHAT:
                case ALIMENTATION_CONSOMMATION:
                    return R.drawable.ble;
                case SOINS:
                    // Couvre Médicament/Autre ET Vaccination depuis la fusion des deux
                    // écrans/boutons mobile — voir SaisieType.SOINS. Pas d'icône dédiée à
                    // la vaccination ici : la liste n'a plus qu'une icône par SaisieType.
                    return R.drawable.ic_trousse_secours;
                case ENTRETIEN:
                    return R.drawable.ic_entretien;
                case MORTALITE:
                    return android.R.drawable.ic_dialog_alert;
                case REFORME:
                case VENTE_REFORME:
                    return R.drawable.reforme;
                case TRANSACTION_ENTREE:
                    return android.R.drawable.arrow_down_float;
                case TRANSACTION_SORTIE:
                    return android.R.drawable.arrow_up_float;
                default:
                    return android.R.drawable.ic_dialog_info;
            }
        }
    }
}
