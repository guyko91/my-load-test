package com.dw.idstrust.loadtesttoy.service;

import java.util.Map;

/**
 * K6 부하 테스트를 제어하는 서비스 인터페이스.
 * 여러 개의 동시 K6 테스트 실행을 지원하도록 수정됨.
 */
public interface K6ControlService {

    /**
     * 새로운 K6 테스트를 시작합니다.
     *
     * @param testType        테스트 유형 (예: "baseline", "scenario")
     * @param scenario        k6 스크립트 내에서 실행할 시나리오
     * @param rps             초당 요청 수
     * @param durationMinutes 테스트 지속 시간(분)
     * @param vus             가상 사용자 수
     * @param scriptName      실행할 k6 스크립트 파일 이름 (예: "dynamic.js")
     * @return 생성된 테스트의 고유 ID
     */
    String startTest(String testType, String scenario, int rps, int durationMinutes, int vus, String scriptName);

    /**
     * 실행 중인 모든 K6 테스트를 중지합니다.
     */
    void stopAllTests();

    /**
     * 현재 실행 중인 모든 테스트와 마지막으로 완료된 테스트의 상태를 반환합니다.
     *
     * @return 테스트 상태 정보를 담은 Map
     */
    Map<String, Object> getStatus();
}