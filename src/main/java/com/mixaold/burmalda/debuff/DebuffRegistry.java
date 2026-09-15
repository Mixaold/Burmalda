package com.mixaold.burmalda.debuff;

import com.mixaold.burmalda.debuff.group.*;
import com.mixaold.burmalda.debuff.solo.*;
import com.mixaold.burmalda.util.BurmaldaLogger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class DebuffRegistry {

    private static final List<Debuff> SOLO_DEBUFFS  = new ArrayList<>();
    private static final List<Debuff> GROUP_DEBUFFS = new ArrayList<>();
    private static final Map<String, Debuff> SOLO_BY_ID  = new HashMap<>();
    private static final Map<String, Debuff> GROUP_BY_ID = new HashMap<>();
    private static final Random RANDOM = new Random();

    public static void register() {
        // Solo debuffs
        SOLO_DEBUFFS.add(new ObshchagaDebuff());
        SOLO_DEBUFFS.add(new KvantovyRyukzakDebuff());
        SOLO_DEBUFFS.add(new AntigravitatsiyaDebuff());
        SOLO_DEBUFFS.add(new PyanyPilotDebuff());
        SOLO_DEBUFFS.add(new DrozhaschieRukiDebuff());
        SOLO_DEBUFFS.add(new PritiyazheniDebuff());
        SOLO_DEBUFFS.add(new KleptomanDebuff());
        SOLO_DEBUFFS.add(new EkhoUronaDebuff());
        SOLO_DEBUFFS.add(new RemontnikDebuff());
        SOLO_DEBUFFS.add(new ZerkalnyyMirDebuff());
        SOLO_DEBUFFS.add(new SlepoeDoverieDebuff());
        SOLO_DEBUFFS.add(new BombaDebuff());
        SOLO_DEBUFFS.add(new FeromonDebuff());
        SOLO_DEBUFFS.add(new RadioaktivnyDebuff());
        SOLO_DEBUFFS.add(new TelefonistDebuff());
        SOLO_DEBUFFS.add(new ObratnayaZhiznDebuff());
        SOLO_DEBUFFS.add(new TryapichnayaKuklaDebuff());
        SOLO_DEBUFFS.add(new KotShrodingeraDebuff());
        SOLO_DEBUFFS.add(new GoryachayaKartoshkaDebuff());
        SOLO_DEBUFFS.add(new MotygaSudbyDebuff());
        SOLO_DEBUFFS.add(new LunatikDebuff());
        SOLO_DEBUFFS.add(new DublyorDebuff());
        SOLO_DEBUFFS.add(new TelekinezDebuff());
        SOLO_DEBUFFS.add(new ObratnyGolodDebuff());
        SOLO_DEBUFFS.add(new ShakhedDebuff());
        SOLO_DEBUFFS.add(new ChernayaDyraDebuff());
        SOLO_DEBUFFS.add(new VspylchivyyBorovDebuff());
        SOLO_DEBUFFS.add(new DezhaVuDebuff());
        SOLO_DEBUFFS.add(new KollektoryDebuff());
        SOLO_DEBUFFS.add(new ChuzhieRukiDebuff());
        SOLO_DEBUFFS.add(new PlakhayaPamyatDebuff());
        SOLO_DEBUFFS.add(new NablyudatelDebuff());
        SOLO_DEBUFFS.add(new TyotaValyaDebuff());
        SOLO_DEBUFFS.add(new NetDebuff());
        SOLO_DEBUFFS.add(new FilosofDebuff());
        SOLO_DEBUFFS.add(new KorpPismoDebuff());
        SOLO_DEBUFFS.add(new NeGlavnyyGeroyDebuff());
        SOLO_DEBUFFS.add(new KolesoUdachiDebuff());
        SOLO_DEBUFFS.add(new SosedSverkhuDebuff());
        SOLO_DEBUFFS.add(new TekhpodderzhkaDebuff());
        SOLO_DEBUFFS.add(new PlanovyeRabotyDebuff());
        SOLO_DEBUFFS.add(new SobesedovanieDebuff());
        SOLO_DEBUFFS.add(new EffektDominoDebuff());
        SOLO_DEBUFFS.add(new RazlomyDebuff());
        SOLO_DEBUFFS.add(new SlavikiDebuff());
        SOLO_DEBUFFS.add(new DoprosDebuff());

        // Group debuffs (20)
        GROUP_DEBUFFS.add(new GromIzMolniyDebuff());
        GROUP_DEBUFFS.add(new SlepoyAuktionDebuff());
        GROUP_DEBUFFS.add(new DozhdSvineyDebuff());
        GROUP_DEBUFFS.add(new NochStrakhaDebuff());
        GROUP_DEBUFFS.add(new KontsertDebuff());
        GROUP_DEBUFFS.add(new ZooparkDebuff());
        GROUP_DEBUFFS.add(new LotereaSmertiDebuff());
        GROUP_DEBUFFS.add(new KonfettiDebuff());
        GROUP_DEBUFFS.add(new KollektivnySonDebuff());
        GROUP_DEBUFFS.add(new MagazinZakrytDebuff());
        GROUP_DEBUFFS.add(new EpidemiyaDebuff());
        GROUP_DEBUFFS.add(new InversiyaKhodaDebuff());
        GROUP_DEBUFFS.add(new KorporativDebuff());
        GROUP_DEBUFFS.add(new ChernayaPyatnotsaDebuff());
        GROUP_DEBUFFS.add(new GravitatsionnyShormDebuff());
        GROUP_DEBUFFS.add(new MassovoeVoskresenieDebuff());
        GROUP_DEBUFFS.add(new ApokalipsisDebuff());
        GROUP_DEBUFFS.add(new TeleportRuletkaDebuff());
        GROUP_DEBUFFS.add(new NulevayaGravitatsiyaDebuff());
        GROUP_DEBUFFS.add(new MeteoroitnyStormDebuff());
        GROUP_DEBUFFS.add(new VoennayaChastDebuff());
        GROUP_DEBUFFS.add(new ApokalipsisZastroyshchikaDebuff());
        GROUP_DEBUFFS.add(new YashchikPandoryDebuff());

        SOLO_DEBUFFS.forEach(d  -> SOLO_BY_ID.put(d.getId(), d));
        GROUP_DEBUFFS.forEach(d -> GROUP_BY_ID.put(d.getId(), d));
        BurmaldaLogger.info("Registered " + SOLO_DEBUFFS.size() + " solo and " + GROUP_DEBUFFS.size() + " group debuffs.");
    }

    public static Debuff randomSolo() {
        if (SOLO_DEBUFFS.isEmpty()) throw new IllegalStateException("No solo debuffs registered");
        return SOLO_DEBUFFS.get(RANDOM.nextInt(SOLO_DEBUFFS.size()));
    }

    public static Debuff randomGroup() {
        if (GROUP_DEBUFFS.isEmpty()) throw new IllegalStateException("No group debuffs registered");
        return GROUP_DEBUFFS.get(RANDOM.nextInt(GROUP_DEBUFFS.size()));
    }

    public static Debuff getSoloById(String id)  { return SOLO_BY_ID.get(id);  }
    public static Debuff getGroupById(String id) { return GROUP_BY_ID.get(id); }

    public static List<Debuff> getSoloDebuffs() { return SOLO_DEBUFFS; }
    public static List<Debuff> getGroupDebuffs() { return GROUP_DEBUFFS; }
}
