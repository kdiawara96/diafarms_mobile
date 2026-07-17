package com.mobile.diafarms.ui.saisie;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.mobile.diafarms.R;
import com.mobile.diafarms.activity.history.HistoriqueCollecteActivity;
import com.mobile.diafarms.data.LocalDatabase;
import com.mobile.diafarms.models.CollecteOeufs;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

public class SaisieOeufsActivity extends AppCompatActivity {

    private TextView tvProjetTitre, tvDate, tvResume;
    private Spinner spinnerBatiment;
    private EditText etQuantite, etOeufsCasses;
    private Button btnValider;
    private TextView btnBack, btnHistorique;

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
        etQuantite = findViewById(R.id.etQuantite);
        etOeufsCasses = findViewById(R.id.etOeufsCasses);
        tvResume = findViewById(R.id.tvResume);
        btnValider = findViewById(R.id.btnValider);
        btnBack = findViewById(R.id.btnBack);
        btnHistorique = findViewById(R.id.btnHistorique); // NOUVEAU
    }

    private void setupUI() {
        tvProjetTitre.setText(projetTitre);
        tvDate.setText(dateFormat.format(new Date()));

        String[] batiments = {"Bâtiment A", "Bâtiment B", "Bâtiment C", "Poulailler principal"};
        ArrayAdapter<String> adapterBat = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, batiments);
        adapterBat.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerBatiment.setAdapter(adapterBat);
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());
        btnValider.setOnClickListener(v -> validerSaisie());

        // NOUVEAU : Bouton historique
        btnHistorique.setOnClickListener(v -> {
            Intent intent = new Intent(this, HistoriqueCollecteActivity.class);
            intent.putExtra("projet_id", projetId);
            intent.putExtra("projet_titre", projetTitre);
            startActivity(intent);
        });
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

        tvResume.setText(String.format("Total: %d œufs vendables (%d cassés)", vendables, casses));

        if (casses > total * 0.05) {
            tvResume.setTextColor(getColor(android.R.color.holo_red_dark));
        } else {
            tvResume.setTextColor(getColor(R.color.green_primary));
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
        collecte.setBatiment(spinnerBatiment.getSelectedItem().toString());
        collecte.setOeufsCasses(casses);
        collecte.setSaisiPar(userId);
        collecte.setSyncStatus("local");

        // Sauvegarde et feedback
        Toast.makeText(this,
                String.format("✓ %d œufs enregistrés dans %s", quantite - casses, collecte.getBatiment()),
                Toast.LENGTH_SHORT).show();

        // Optionnel: rediriger vers l'historique ou vider les champs
        // finish();
    }
}