package com.cpe.simulator.cwmp;

import com.cpe.simulator.common.AppConfig;
import com.cpe.simulator.model.DataModel;

import java.util.List;

public interface DeviceContext {
    DataModel getDataModel();
    String getDeviceId();
    String getSerialNumber();
    String getOui();
    String getProductClass();
    AppConfig getConfig();
    CwmpSession getSession();
    void triggerInform(String eventCode);
    void scheduleReboot(int delaySeconds);
    void scheduleFactoryReset(int delaySeconds);
    void addPendingTransfer(CwmpSession.PendingTransfer transfer);
    List<CwmpSession.PendingTransfer> getPendingTransfers();
    void clearPendingTransfers();
}
