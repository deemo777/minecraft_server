package com.htk.core.tools;

import java.nio.file.*;

/** Export review tables using the exact runtime formulas rather than copied constants. */
public final class EquipmentStatsReport {
    private static String n(double value){return Progression.label(value);}
    private static String durability(ToolType type,int level){return level==25?"무한":Integer.toString(ToolPerformance.maxDurability(type,level));}
    private static void row(StringBuilder out,Object... cells){out.append('|');for(Object cell:cells)out.append(' ').append(cell).append(" |");out.append('\n');}
    private static void header(StringBuilder out,String title,String... columns){
        out.append("\n## ").append(title).append("\n\n");row(out,(Object[])columns);
        out.append('|');for(var ignored:columns)out.append("---|");out.append('\n');
    }
    public static void main(String[] args) throws Exception {
        var out=new StringBuilder("# 장비 강화 단계별 실제 수치\n\n런타임 계산식으로 생성한 0~25강 전체 표입니다. 곡괭이 행운은 10강부터 적용되며 연결 광석은 21강부터 한 단계당 1개씩 추가됩니다. 무기는 (강화/25)^1.5 곡선으로 성장하며 대검은 공격력이 커지는 대신 공속이 감소합니다. 단검의 주변 피해 비율과 최대 타격 수는 고정입니다. 소수 드롭 보정·작업 칸 수는 확률로 반영하므로 표의 범위는 평균 목표 칸 수입니다. 곡괭이·도끼·괭이는 5~19강 최대 내구도 1,561, 20강부터 2,031이며 내구성은 최대치를 부풀리는 대신 실제 소모 확률을 낮춥니다. 낚싯대 최대 내구도 64, 물뿌리개 25에 같은 보정을 적용합니다. 25강 무한 내구도·무한 물·불/용암 보호는 최종 보상으로 유지합니다. 재질과 외형 변화는 성능 수치의 구간 고정을 의미하지 않습니다.\n\n채굴/벌목 속도는 적합한 블록에서의 기본 수치이며 경도·수중·공중 상태에 따라 실제 시간은 달라집니다. 연결 광석은 20강까지 추가 0개, 21~25강은 총 2/3/4/5/6개로 고정합니다. 원목 연결 작업은 기존 최대 4,096개·32개/틱을 유지합니다. 내구도 소모 확률은 도구의 경우 5강 기본·10강 내구성 I·15강 III·20강 IV 상당의 수치 보정, 무기는 0~24강 100%, 모두 25강 0%입니다.\n");
        header(out,"곡괭이","강화","채굴 속도","드롭 보정 강도","연결 광석 추가(평균)","최대 내구도","내구도 소모 확률");
        for(int l=0;l<=25;l++)row(out,l,n(ToolPerformance.miningSpeed(ToolType.PICKAXE,l)),n(ToolPerformance.fortuneStrength(l)),n(ToolPerformance.expectedExtraBlocks(l)),durability(ToolType.PICKAXE,l),n(ToolPerformance.wearChance(ToolType.PICKAXE,l)*100)+"%");
        header(out,"도끼","강화","벌목 속도","원형 회수 확률","연결 원목 추가(평균)","최대 내구도","내구도 소모 확률");
        for(int l=0;l<=25;l++)row(out,l,n(ToolPerformance.miningSpeed(ToolType.AXE,l)),n(ToolPerformance.preserveChance(l)*100)+"%",n(ToolPerformance.expectedWoodExtraBlocks(l)),durability(ToolType.AXE,l),n(ToolPerformance.wearChance(ToolType.AXE,l)*100)+"%");
        header(out,"괭이","강화","수확 칸 수(평균)","드롭 보정 강도","최대 내구도","내구도 소모 확률");
        for(int l=0;l<=25;l++)row(out,l,n(HarvestRules.expectedCells(l)),n(ToolPerformance.hoeFortuneStrength(l)),durability(ToolType.HOE,l),n(ToolPerformance.wearChance(ToolType.HOE,l)*100)+"%");
        out.append("\n작물 보정은 밀/비트의 씨앗·당근/감자 작물에 추가 추첨(회당 약 57.14%)으로 적용하며, 네더 사마귀는 균등 개수 가산입니다. 미성숙 작물에는 보정하지 않습니다. 소수 강도는 인접한 정수 강도를 확률 혼합합니다.\n");
        header(out,"낚싯대","강화","대기·접근 시간 배수","대기 단축","물고기 1개 추가 확률","최대 내구도","내구도 소모 확률");
        for(int l=0;l<=25;l++)row(out,l,n(ToolPerformance.fishingWaitFactor(l))+"배",n((1-ToolPerformance.fishingWaitFactor(l))*100)+"%",n(ToolPerformance.fishingExtraChance(l)*100)+"%",durability(ToolType.FISHING_ROD,l),n(ToolPerformance.wearChance(ToolType.FISHING_ROD,l)*100)+"%");
        out.append("\n기존 낚시 스킬의 보너스는 별도로 유지합니다. 추가 획득은 일반 물고기 4종만 대상으로 보물·잡동사니는 복제하지 않습니다. 던진 낚싯대의 인스턴스와 강화 단계를 낚싯바늘에 연결하고 손 교체/다른 낚싯대 사용 시 추가 보상을 주지 않습니다. 기본 보물 확률은 변경하지 않습니다.\n");
        header(out,"물뿌리개","강화","물주기 칸 수(평균)","보충당 사용 횟수","최대 내구도","내구도 소모 확률");
        for(int l=0;l<=25;l++)row(out,l,n(WateringCanRules.expectedCells(l)),l==25?"무한":WateringCanRules.capacity(l),durability(ToolType.WATERING_CAN,l),n(ToolPerformance.wearChance(ToolType.WATERING_CAN,l)*100)+"%");
        for(var type:new ToolType[]{ToolType.DAGGER,ToolType.TACHI,ToolType.GREATSWORD}) {
            header(out,type.korean,"강화","순수 공격력","초당 공격","사거리","단일 이론 DPS","주변 피해 비율","최대 타격 수","최대 내구도");
            for(int l=0;l<=25;l++)row(out,l,n(WeaponPerformance.damage(type,l)),n(WeaponPerformance.speed(type,l)),n(WeaponPerformance.reach(type,l)),n(WeaponPerformance.damage(type,l)*WeaponPerformance.speed(type,l)),n(WeaponPerformance.sweepRatio(type,l)*100)+"%",WeaponPerformance.maxTargets(type),durability(type,l));
        }
        out.append("\n무기 DPS는 방어력·치명타·스킬·범위 피해를 제외한 값입니다. 휩쓸기 발동 시 주변 피해는 1+순수 공격력×주변 피해 비율입니다. 실제 인챈트와 임시 인챈트 도구는 사용하지 않습니다. 최대 타격 수는 직접 대상 포함 단검 2·태도 3·대검 5로 강화와 무관하게 고정하며, 범위 안 주변 대상은 공격 시점 플레이어에 가까운 순서로 선택합니다. 무기 내구도 소모 확률도 0~24강 100%, 25강 0%입니다.\n\n최대 내구도 변경·재질 전환·접속 갱신 시 기존 손상 비율을 보존합니다. 실제 강화 성공 시에는 기존 규칙대로 완전 회복하며 물 잔량을 무료로 채우지 않습니다.\n");
        out.append("\n## 25강 무기 고유 효과\n\n- 단검 추격: 같은 대상에 3회 유효 직접 명중 시 이동 속도 +20% 2초, 재발동 대기 5초. 연속 명중 간격은 최대 2초이며 대상/무기 인스턴스/손 변경 시 연속 기록을 초기화합니다.\n- 태도 공방일체: 유효 직접 명중 후 받는 피해 10% 감소 2초, 재명중 시 시간만 갱신합니다.\n- 대검 압도: 같은 공격으로 3마리 이상에게 실제 피해가 들어가면 그 공격에 맞힌 대상들을 1초간 30% 둔화합니다. 재발동 대기 3초. 취소/피해 0 타격은 집계하지 않습니다.\n- 단검/태도는 공격 게이지 85% 이상인 직접 타격만 특성에 집계합니다. 세 효과는 자동 발동하며 인챈트/물약 없이 수치로 처리합니다. 공격 무기를 바꾸면 자신의 추격/공방일체 효과를 해제하고, 이동 버프·둔화는 자신의 전용 속성만 제거해 다른 효과를 보존합니다.\n");
        Path target=Path.of("docs/기능/장비강화_단계별수치.md");Files.writeString(target,out);System.out.println("Exported "+target);
    }
}
