package com.mobile.diafarms.util;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.mobile.diafarms.network.dto.AlimentationCreateRequest;
import com.mobile.diafarms.network.dto.TransactionCreateRequest;
import com.mobile.diafarms.network.dto.VenteOeufsCreateRequest;

import org.junit.Test;

public class MontantsInhabituelsTest {

    private static final MontantsInhabituels.Format F = m -> String.format(java.util.Locale.FRANCE, "%,.0f F", m);

    private static MontantsInhabituels.Reperes reperes() {
        MontantsInhabituels.Reperes r = new MontantsInhabituels.Reperes();
        r.prix.put("ALIMENT_KG", 500.0);
        r.prix.put("OEUF_BON", 83.33);
        r.maxParCategorie.put("Location", 20000.0);
        r.maxParCategorie.put("Aliment", 350000.0);
        return r;
    }

    private static String premier(Object req, MontantsInhabituels.Reperes r) {
        for (MontantsInhabituels.Controle c : MontantsInhabituels.controles(req)) {
            String m = MontantsInhabituels.message(c, r, F);
            if (m != null) return m;
        }
        return null;
    }

    @Test
    public void alimentA50000LeKilo() {
        AlimentationCreateRequest a = new AlimentationCreateRequest();
        a.quantiteKg = 1000.0;
        a.coutTotal = 50_000_000.0;
        String m = premier(a, reperes());
        assertNotNull(m);
        assertTrue(m.contains("le kilo d'aliment"));
    }

    @Test
    public void alimentAuPrixHabituelEnGrosseQuantite() {
        AlimentationCreateRequest a = new AlimentationCreateRequest();
        a.quantiteKg = 10_000.0;
        a.coutTotal = 5_000_000.0;
        assertNull(premier(a, reperes()));
    }

    @Test
    public void locationA200Millions() {
        TransactionCreateRequest t = new TransactionCreateRequest();
        t.montant = 200_000_000.0;
        t.categorie = "Location";
        assertNotNull(premier(t, reperes()));
    }

    @Test
    public void petitMontantJamais() {
        TransactionCreateRequest t = new TransactionCreateRequest();
        t.montant = 90_000.0;
        t.categorie = "Location";
        assertNull(premier(t, reperes()));
    }

    @Test
    public void sansHistoriqueSeuilDixMillions() {
        TransactionCreateRequest t = new TransactionCreateRequest();
        t.categorie = "Transport";
        t.montant = 9_000_000.0;
        assertNull(premier(t, reperes()));
        t.montant = 10_000_000.0;
        assertNotNull(premier(t, null));
    }

    @Test
    public void oeufsMontantRapporteAvecUnZeroDeTrop() {
        VenteOeufsCreateRequest v = new VenteOeufsCreateRequest();
        v.quantiteOeufs = 300;
        v.prixUnitaire = 2500.0 / 30;
        v.montant = 25_000.0;
        v.montantRapporte = 250_000.0;
        String m = premier(v, reperes());
        assertNotNull(m);
        assertTrue(m.contains("montant rapporté"));
    }
}
