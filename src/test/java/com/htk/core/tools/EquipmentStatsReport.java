package com.htk.core.tools;

import java.nio.file.*;

/** Export review tables using the exact runtime formulas rather than copied constants. */
public final class EquipmentStatsReport {
    private static String n(double value){return Progression.label(value);}
    private static String durability(ToolType type,int level){return level==25?"무한":Integer.toString(ToolPerformance.maxDurability(type,level));}
    private static String oreChances(int level) {
        var parts=new java.util.ArrayList<String>();
        for(int multiplier=1;multiplier<=5;multiplier++) {
            double probability=FortuneDrops.oreProbability(ToolPerformance.fortuneStrength(level),multiplier);
            if(probability>0)parts.add(multiplier+"배 "+n(probability*100)+"%");
        }
        return String.join(" / ",parts);
    }
    private static void row(StringBuilder out,Object... cells){out.append('|');for(Object cell:cells)out.append(' ').append(cell).append(" |");out.append('\n');}
    private static void header(StringBuilder out,String title,String... columns){
        out.append("\n## ").append(title).append("\n\n");row(out,(Object[])columns);
        out.append('|');for(var ignored:columns)out.append("---|");out.append('\n');
    }
    /** Keep source tables compact: count Korean headings as characters, without double-width padding. */
    private static int width(String text) {
        return text.codePointCount(0,text.length());
    }
    private static String alignTables(String markdown) {
        String[] lines=markdown.split("\n",-1);var result=new StringBuilder();
        for(int start=0;start<lines.length;) {
            if(!lines[start].startsWith("|")) {
                result.append(lines[start++]);
                if(start<lines.length)result.append('\n');
                continue;
            }
            int end=start;var rows=new java.util.ArrayList<String[]>();
            while(end<lines.length && lines[end].startsWith("|")) {
                String line=lines[end++];
                rows.add(java.util.Arrays.stream(line.substring(1,line.lastIndexOf('|')).split("\\|",-1))
                        .map(String::strip).toArray(String[]::new));
            }
            int[] widths=new int[rows.getFirst().length];java.util.Arrays.fill(widths,3);
            for(int r=0;r<rows.size();r++)if(r!=1)for(int c=0;c<widths.length;c++)
                widths[c]=Math.max(widths[c],width(rows.get(r)[c]));
            for(int r=0;r<rows.size();r++) {
                result.append('|');
                for(int c=0;c<widths.length;c++) {
                    String cell=rows.get(r)[c];
                    if(r==1)cell="-".repeat(widths[c]);
                    String padding=" ".repeat(widths[c]-width(cell));
                    result.append(' ').append(r>1?padding+cell:cell+padding).append(" |");
                }
                result.append('\n');
            }
            start=end;
        }
        return result.toString();
    }
    public static void main(String[] args) throws Exception {
        var out=new StringBuilder("# 장비 강화 단계별 실제 수치\n\n런타임 계산식으로 생성한 0~25강 전체 표입니다. 0강 철·5강 순수 다이아·10강 효율 III/행운 I/내구성 I·15강 효율 V/행운 III/내구성 III·20강 네더라이트 효율 VI/행운 3.5/내구성 IV·25강 효율 VII/행운 IV/무한 내구도를 기준으로 보간합니다. 속도는 완성된 기준점 사이를 선형 보간합니다. 효율의 소수 단계는 최종 작업 속도로 반영합니다.\n\n0~24강은 바닐라와 같은 양의 내구도를 매번 소모합니다. 내구성 상당 효과는 총 내구도에 반영하여 I=2배, III=4배, IV=5배입니다. 재질의 기본 내구도도 0~5강 철→다이아, 15~20강 다이아→네더라이트로 보간합니다. 낚싯대/물뿌리개 바탕 내구도는 64/25입니다. 25강만 무한 내구도입니다. 기존 장비 갱신 시 손상 비율을 보존합니다.\n\n원목 추가 수와 작업 칸 수는 고정 정수입니다. 곡괭이 연결 광석은 20강까지 직접 블록만, 21~25강 총 2/3/4/5/6개를 유지합니다. 채굴/벌목 속도는 적합한 블록에서의 기본 수치이며 경도·수중·공중 상태에 따라 실제 시간은 달라집니다.\n");
        header(out,"곡괭이","강화","채굴 속도","광물 드롭 배율별 확률","연결 광석 추가","최대 내구도","내구도 소모율");
        for(int l=0;l<=25;l++)row(out,l,n(ToolPerformance.miningSpeed(ToolType.PICKAXE,l)),oreChances(l),n(ToolPerformance.expectedExtraBlocks(l)),durability(ToolType.PICKAXE,l),n(ToolPerformance.wearChance(ToolType.PICKAXE,l)*100)+"%");
        out.append("\n곡괭이 확률은 다이아·석탄·철·구리·청금석 등의 기본 드롭량에 곱하는 배율입니다. 다이아 기본 1개에서는 배율이 개수와 같습니다. 정수 행운 단계의 바닐라 분포를 기준으로 각 결과 확률을 보간한 뒤 최종 결과를 한 번 추첨하며, 중간에 행운 단계를 추첨하지 않습니다. 9강은 2배 26.67%, 10강은 2배 33.33%입니다. 레드스톤은 배율 대신 추가 개수 0~행운 단계의 분포를 같은 방식으로 계산하고, 자갈은 부싯돌 확률표를 보간합니다.\n");
        header(out,"도끼","강화","벌목 속도","원형 회수","연결 원목 추가","최대 내구도","내구도 소모율");
        for(int l=0;l<=25;l++)row(out,l,n(ToolPerformance.miningSpeed(ToolType.AXE,l)),l>=15?"확정":"없음",n(ToolPerformance.expectedWoodExtraBlocks(l)),durability(ToolType.AXE,l),n(ToolPerformance.wearChance(ToolType.AXE,l)*100)+"%");
        header(out,"괭이","강화","수확 칸 수","드롭 배율","최대 내구도","내구도 소모율");
        for(int l=0;l<=25;l++)row(out,l,n(HarvestRules.expectedCells(l)),l<5?"1배":l<15?"2배 "+n(ToolPerformance.cropBonusChance(l)*100)+"%":"기본 2배 / 3배 "+n(ToolPerformance.cropBonusChance(l)*100)+"%",durability(ToolType.HOE,l),n(ToolPerformance.wearChance(ToolType.HOE,l)*100)+"%");
        out.append("\n괭이는 성숙한 작물의 원래 바닐라 드롭 전체에 배율을 적용합니다. 5~14강은 2배 확률 10~100%, 15~25강은 기본 2배에 3배 확률 10~100%입니다. 14강 기대값 2배보다 15강 기대값 2.1배가 높습니다. 미성숙 작물은 보정하지 않고 자동 재파종하지 않습니다.\n");
        header(out,"낚싯대","강화","대기·접근 시간 배수","대기 단축","물고기 1개 추가 확률","최대 내구도","내구도 소모율");
        for(int l=0;l<=25;l++)row(out,l,n(ToolPerformance.fishingWaitFactor(l))+"배",n((1-ToolPerformance.fishingWaitFactor(l))*100)+"%",n(ToolPerformance.fishingExtraChance(l)*100)+"%",durability(ToolType.FISHING_ROD,l),n(ToolPerformance.wearChance(ToolType.FISHING_ROD,l)*100)+"%");
        out.append("\n기존 낚시 스킬의 보너스는 별도로 유지합니다. 추가 획득은 일반 물고기 4종만 대상으로 보물·잡동사니는 복제하지 않습니다. 던진 낚싯대의 인스턴스와 강화 단계를 낚싯바늘에 연결하고 손 교체/다른 낚싯대 사용 시 추가 보상을 주지 않습니다. 기본 보물 확률은 변경하지 않습니다.\n");
        header(out,"물뿌리개","강화","물주기 칸 수","보충당 사용 횟수","최대 내구도","내구도 소모율");
        for(int l=0;l<=25;l++)row(out,l,n(WateringCanRules.expectedCells(l)),l==25?"무한":WateringCanRules.capacity(l),durability(ToolType.WATERING_CAN,l),n(ToolPerformance.wearChance(ToolType.WATERING_CAN,l)*100)+"%");
        for(var type:new ToolType[]{ToolType.DAGGER,ToolType.TACHI,ToolType.GREATSWORD}) {
            header(out,type.korean,"강화","순수 공격력","초당 공격","사거리","단일 이론 DPS","주변 피해 비율","최대 타격 수","최대 내구도");
            for(int l=0;l<=25;l++)row(out,l,n(WeaponPerformance.damage(type,l)),n(WeaponPerformance.speed(type,l)),n(WeaponPerformance.reach(type,l)),n(WeaponPerformance.damage(type,l)*WeaponPerformance.speed(type,l)),n(WeaponPerformance.sweepRatio(type,l)*100)+"%",WeaponPerformance.maxTargets(type),durability(type,l));
        }
        out.append("\n무기 DPS는 방어력·치명타·스킬·범위 피해를 제외한 값입니다. 휩쓸기 발동 시 주변 피해는 1+순수 공격력×주변 피해 비율입니다. 실제 인챈트와 임시 인챈트 도구는 사용하지 않습니다. 최대 타격 수는 직접 대상 포함 단검 2·태도 3·대검 5로 강화와 무관하게 고정하며, 범위 안 주변 대상은 공격 시점 플레이어에 가까운 순서로 선택합니다. 무기 내구도 소모율도 0~24강 100%, 25강 0%입니다.\n\n최대 내구도 변경·재질 전환·접속 갱신 시 기존 손상 비율을 보존합니다. 실제 강화 성공 시에는 기존 규칙대로 완전 회복하며 물 잔량을 무료로 채우지 않습니다.\n");
        out.append("\n## 25강 무기 고유 효과\n\n- 단검 추격: 같은 대상에 3회 유효 직접 명중 시 이동 속도 +20% 2초, 재발동 대기 5초. 연속 명중 간격은 최대 2초이며 대상/무기 인스턴스/손 변경 시 연속 기록을 초기화합니다.\n- 태도 공방일체: 유효 직접 명중 후 받는 피해 10% 감소 2초, 재명중 시 시간만 갱신합니다.\n- 대검 압도: 같은 공격으로 3마리 이상에게 실제 피해가 들어가면 그 공격에 맞힌 대상들을 1초간 30% 둔화합니다. 재발동 대기 3초. 취소/피해 0 타격은 집계하지 않습니다.\n- 단검/태도는 공격 게이지 85% 이상인 직접 타격만 특성에 집계합니다. 세 효과는 자동 발동하며 인챈트/물약 없이 수치로 처리합니다. 공격 무기를 바꾸면 자신의 추격/공방일체 효과를 해제하고, 이동 버프·둔화는 자신의 전용 속성만 제거해 다른 효과를 보존합니다.\n");
        Path target=Path.of("docs/기능/장비강화_단계별수치.md");Files.writeString(target,alignTables(out.toString()));System.out.println("Exported "+target);
    }
}
