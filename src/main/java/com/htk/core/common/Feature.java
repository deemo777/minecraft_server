package com.htk.core.common;

/** 기능 하나(메뉴, 도감, ...)의 생명주기 */
public interface Feature {

    /** 서버가 켜질 때 한 번: 설정 읽기, 이벤트·명령어 등록 */
    void enable();

    /** /servercore reload 할 때: 설정만 다시 읽기 */
    void reload();

    /** 서버가 꺼질 때: 데이터 저장 */
    void disable();
}
