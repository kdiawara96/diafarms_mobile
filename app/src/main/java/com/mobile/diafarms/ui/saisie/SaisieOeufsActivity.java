package com.mobile.diafarms.ui.saisie;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;



import com.mobile.diafarms.R;
import com.mobile.diafarms.data.LocalDatabase;
import com.mobile.diafarms.models.CollecteOeufs;

public class SaisieOeufsActivity extends AppCompatActivity {


    private TextView tvProjetTitre, tvDate, tvResume;
    private Spinner spinnerBatiment, spinnerCategorie;
    private EditText etQuantite, etOeufsCasses;
    private Button btnValider;
    private ImageButton btnBack;

    private String projetId, userId, projetTitre;
    private LocalDatabase localDatabase;
    private SimpleDateFormat dateFormat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_saisie_oeufs);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_oeufs), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        // Récupération des extras
        projetId = getIntent().getStringExtra("projet_id");
        userId = getIntent().getStringExtra("user_id");
        projetTitre = getIntent().getStringExtra("projet_titre");

        localDatabase = new LocalDatabase(this);
        dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE);

        bindViews();
        setupUI();
        setupListeners();
        setupCalculAuto();
    }

    private void bindViews() {
        tvProjetTitre = findViewById(R.id.tvProjetTitre);
        tvDate = findViewById(R.id.tvDate);
        spinnerBatiment = findViewById(R.id.spinnerBatiment);
        spinnerCategorie = findViewById(R.id.spinnerCategorie);
        etQuantite = findViewById(R.id.etQuantite);
        etOeufsCasses = findViewById(R.id.etOeufsCasses);
        tvResume = findViewById(R.id.tvResume);
        btnValider = findViewById(R.id.btnValider);
        btnBack = findViewById(R.id.btnBack);
    }

    private void setupUI() {
        tvProjetTitre.setText(projetTitre);
        tvDate.setText(dateFormat.format(new Date()));

        // Bâtiments (à personnaliser selon votre ferme)
        String[] batiments = {"Bâtiment A", "Bâtiment B", "Bâtiment C", "Poulailler principal"};
        ArrayAdapter<String> adapterBat = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, batiments);
        adapterBat.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerBatiment.setAdapter(adapterBat);

        // Catégories d'œufs standards
        String[] categories = {"Extra (>65g)", "Grand (60-65g)", "Moyen (55-60g)",
                "Petit (50-55g)", "Poulette (<50g)"};
        ArrayAdapter<String> adapterCat = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, categories);
        adapterCat.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCategorie.setAdapter(adapterCat);
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());
        btnValider.setOnClickListener(v -> validerSaisie());
    }

    private void setupCalculAuto() {
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                calculerResume();
            }
            @Override public void afterTextChanged(Editable s) {}
        };
        etQuantite.addTextChangedListener(watcher);
        etOeufsCasses.addTextChangedListener(watcher);
    }

    private void calculerResume() {
        int total = parseIntSafe(etQuantite.getText().toString());
        int casses = parseIntSafe(etOeufsCasses.getText().toString());
        int vendables = Math.max(0, total - casses);

        tvResume.setText(String.format("Total: %d œufs vendables (%d cassés)",
                vendables, casses));

        // Couleur selon le taux de casse
        if (casses > total * 0.05) { // > 5% de casse
            tvResume.setTextColor(getColor(android.R.color.holo_red_dark));
        } else {
            tvResume.setTextColor(getColor(R.color.green_primary)); // votre couleur verte
        }
    }

    private int parseIntSafe(String s) {
        try {
            return s.isEmpty() ? 0 : Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void validerSaisie() {
        String quantiteStr = etQuantite.getText().toString();

        if (quantiteStr.isEmpty() || Integer.parseInt(quantiteStr) == 0) {
            Toast.makeText(this, "Veuillez saisir une quantité", Toast.LENGTH_SHORT).show();
            return;
        }

        int quantite = Integer.parseInt(quantiteStr);
        int casses = parseIntSafe(etOeufsCasses.getText().toString());

        CollecteOeufs collecte = new CollecteOeufs();
        collecte.setId(UUID.randomUUID().toString());
        collecte.setProjetId(projetId);
        collecte.setDate(dateFormat.format(new Date()));
        collecte.setQuantite(quantite);
        collecte.setCategorie(spinnerCategorie.getSelectedItem().toString());
        collecte.setBatiment(spinnerBatiment.getSelectedItem().toString());
        collecte.setOeufsCasses(casses);
        collecte.setSaisiPar(userId);
        collecte.setSyncStatus("local");

//        long result = localDatabase.insertCollecteOeufs(collecte);
//
//        if (result > 0) {
//            int vendables = quantite - casses;
//            Toast.makeText(this,
//                    String.format("✓ Collecte enregistrée: %d œufs", vendables),
//                    Toast.LENGTH_SHORT).show();
//            finish();
//        } else {
//            Toast.makeText(this, "Erreur lors de la sauvegarde", Toast.LENGTH_SHORT).show();
//        }
    }
}