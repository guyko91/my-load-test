package com.dw.idstrust.loadtesttoy.service;

import java.util.Map;

public interface K6ControlService {
    void startK6Test(String scenario, int rps, int durationMinutes, int vus);

    void startLongScenario(String scenarioName);

    void stopK6Test();

    boolean isK6Running();

    Map<String, String> getLastTestResult();

    Map<String, Object> getK6Status();
}
