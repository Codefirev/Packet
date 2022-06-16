package rip.kirooh.listener;

import rip.kirooh.packet.Packet;

import java.lang.reflect.*;

public class PacketListenerData
{
    private Object instance;
    private Method method;
    private Class packetClass;

    public boolean matches(final Packet packet) {
        return this.packetClass == packet.getClass();
    }

    public PacketListenerData(final Object instance, final Method method, final Class packetClass) {
        this.instance = instance;
        this.method = method;
        this.packetClass = packetClass;
    }

    public Object getInstance() {
        return this.instance;
    }

    public Method getMethod() {
        return this.method;
    }

    public Class getPacketClass() {
        return this.packetClass;
    }
}
