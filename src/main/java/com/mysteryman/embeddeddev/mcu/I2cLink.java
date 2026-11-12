package com.mysteryman.embeddeddev.mcu;

/** Single-controller, open-drain I2C wire protocol, including ACK, NACK and repeated START. */
public final class I2cLink {
    private enum Phase { ADDRESS, RECEIVE, ACK_LOW, ACK_HIGH, ACK_DONE, TRANSMIT, TX_END, MASTER_ACK, MASTER_ACK_END, IGNORE }
    private I2cDevice device;
    private boolean oldSda = true, oldScl = true, active, pullLow, addressed, reading, ack, masterAck;
    private int bits, data, tx, txBit;
    private Phase phase = Phase.IGNORE;
    public void attach(I2cDevice next, boolean sda, boolean scl) {
        if (next != device) { reset(sda, scl); device = next; }
    }
    public I2cDevice device() { return device; }
    public boolean pullLow() { return pullLow; }
    public void reset(boolean sda, boolean scl) {
        if (active && device != null) device.end();
        active = pullLow = addressed = false; phase = Phase.IGNORE; bits = data = 0;
        oldSda = sda; oldScl = scl;
    }
    public void update(boolean sda, boolean scl) {
        if (device == null) { oldSda = sda; oldScl = scl; return; }
        if (oldSda && !sda && oldScl && scl) {
            if (active) device.end();
            active = true; addressed = pullLow = false; bits = data = 0; phase = Phase.ADDRESS;
        } else if (!oldSda && sda && oldScl && scl) {
            if (active) device.end(); active = pullLow = addressed = false; phase = Phase.IGNORE;
        } else if (active && !oldScl && scl) rising(sda);
        else if (active && oldScl && !scl) falling();
        oldSda = sda; oldScl = scl;
    }
    private void rising(boolean sda) {
        switch (phase) {
            case ADDRESS, RECEIVE -> {
                data = (data << 1) | (sda ? 1 : 0);
                if (++bits == 8) {
                    if (phase == Phase.ADDRESS) {
                        reading = (data & 1) != 0;
                        addressed = (data >>> 1) == device.address() && device.begin(reading);
                        ack = addressed;
                    } else ack = addressed && device.write(data & 255);
                    phase = Phase.ACK_LOW;
                }
            }
            case ACK_HIGH -> phase = Phase.ACK_DONE;
            case TRANSMIT -> { if (txBit == 0) phase = Phase.TX_END; }
            case MASTER_ACK -> { masterAck = !sda; phase = Phase.MASTER_ACK_END; }
            default -> {}
        }
    }
    private void falling() {
        switch (phase) {
            case ACK_LOW -> { pullLow = ack; phase = Phase.ACK_HIGH; }
            case ACK_DONE -> {
                pullLow = false; bits = data = 0;
                if (!addressed) phase = Phase.IGNORE;
                else if (reading) transmit(); else phase = Phase.RECEIVE;
            }
            case TRANSMIT -> { txBit--; pullLow = ((tx >>> txBit) & 1) == 0; }
            case TX_END -> { pullLow = false; phase = Phase.MASTER_ACK; }
            case MASTER_ACK_END -> { if (masterAck) transmit(); else { pullLow = false; phase = Phase.IGNORE; } }
            default -> {}
        }
    }
    private void transmit() { tx = device.read() & 255; txBit = 7; pullLow = (tx & 128) == 0; phase = Phase.TRANSMIT; }
}
