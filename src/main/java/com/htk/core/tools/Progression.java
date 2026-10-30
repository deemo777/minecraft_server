package com.htk.core.tools;
/** Linear interpolation between five-level reference points, never tier quantization. */
public final class Progression {
 private Progression() {}
 public static double value(int level,double... anchors) {
  double at=Math.clamp(level,0,25)/5.0;int i=Math.min(4,(int)at);
  return anchors[i]+(anchors[i+1]-anchors[i])*(at-i);
 }
 public static int count(double expected,double roll) {
  int floor=(int)Math.floor(expected);return floor+(roll<expected-floor?1:0);
 }
 public static String label(double value){return java.math.BigDecimal.valueOf(value).setScale(2,java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();}
}
