package rip.kirooh;

import com.google.gson.*;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPubSub;
import rip.kirooh.handler.IncomingPacketHandler;
import rip.kirooh.handler.PacketExceptionHandler;
import rip.kirooh.listener.PacketListener;
import rip.kirooh.listener.PacketListenerData;
import rip.kirooh.packet.Packet;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.Map;

public class Kirooh {

    private static JsonParser PARSER;
    private final String channel;
    private JedisPool jedisPool;
    private JedisPubSub jedisPubSub;
    private List<PacketListenerData> packetListeners;
    private Map<Integer, Class> idToType;
    private Map<Class, Integer> typeToId;

    public Kirooh(final String channel, final String host, final int port, final String password) {
        this.idToType = new HashMap<Integer, Class>();
        this.typeToId = new HashMap<Class, Integer>();
        this.channel = channel;
        this.packetListeners = new ArrayList<PacketListenerData>();
        this.jedisPool = new JedisPool(host, port);
        if (password != null && !password.equals("")) {
            try (final Jedis jedis = this.jedisPool.getResource()) {
                jedis.auth(password);
                System.out.println("[Pidgin] Authenticating..");
            }
        }
        this.setupPubSub();
    }

    public void sendPacket(final Packet packet) {
        this.sendPacket(packet, null);
    }

    public void sendPacket(final Packet packet, final PacketExceptionHandler exceptionHandler) {
        try {
            final JsonObject object = packet.serialize();
            if (object == null) {
                throw new IllegalStateException("Packet cannot generate null serialized data");
            }
            try (final Jedis jedis = this.jedisPool.getResource()) {
                System.out.println("[Kirooh] Attempting to publish packet..");
                try {
                    jedis.publish(this.channel, packet.id() + ";" + object.toString());
                    System.out.println("[Kirooh] Successfully published packet..");
                }
                catch (Exception ex) {
                    System.out.println("[Kirooh] Failed to publish packet..");
                    ex.printStackTrace();
                }
            }
        }
        catch (Exception e) {
            if (exceptionHandler != null) {
                exceptionHandler.onException(e);
            }
        }
    }

    public Packet buildPacket(final int id) {
        if (!this.idToType.containsKey(id)) {
            throw new IllegalStateException("A packet with that ID does not exist");
        }
        try {
            return (Packet) this.idToType.get(id).newInstance();
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException("Could not create new instance of packet type");
        }
    }

    public void registerPacket(final Class clazz) {
        try {
            final int id = (int)clazz.getDeclaredMethod("id", (Class<?>[])new Class[0]).invoke(clazz.newInstance(), (Object[])null);
            if (this.idToType.containsKey(id) || this.typeToId.containsKey(clazz)) {
                throw new IllegalStateException("A packet with that ID has already been registered");
            }
            this.idToType.put(id, clazz);
            this.typeToId.put(clazz, id);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void registerListener(final PacketListener packetListener) {
        for (final Method method : packetListener.getClass().getDeclaredMethods()) {
            if (method.getDeclaredAnnotation(IncomingPacketHandler.class) != null) {
                Class packetClass = null;
                if (method.getParameters().length > 0 && Packet.class.isAssignableFrom(method.getParameters()[0].getType())) {
                    packetClass = method.getParameters()[0].getType();
                }
                if (packetClass != null) {
                    this.packetListeners.add(new PacketListenerData(packetListener, method, packetClass));
                }
            }
        }
    }

    private void setupPubSub() {
        System.out.println("[Kirooh] Setting up PubSup..");
        this.jedisPubSub = new JedisPubSub() {
            public void onMessage(final String channel, final String message) {
                if (channel.equalsIgnoreCase(Kirooh.this.channel)) {
                    try {
                        final String[] args = message.split(";");
                        final Integer id = Integer.valueOf(args[0]);
                        final Packet packet = Kirooh.this.buildPacket(id);
                        if (packet != null) {
                            packet.deserialize(Kirooh.PARSER.parse(args[1]).getAsJsonObject());
                            for (final PacketListenerData data : Kirooh.this.packetListeners) {
                                if (data.matches(packet)) {
                                    data.getMethod().invoke(data.getInstance(), packet);
                                }
                            }
                        }
                    }
                    catch (Exception e) {
                        System.out.println("[Kirooh] Failed to handle message");
                        e.printStackTrace();
                    }
                }
            }
        };
        final Jedis[] jedis = new Jedis[1];
        final Throwable t2 = null;
        ForkJoinPool.commonPool().execute(() -> {
            try {
                jedis[0] = this.jedisPool.getResource();
                try {
                    jedis[0].subscribe(this.jedisPubSub, new String[] { this.channel });
                    System.out.println("[Kirooh] Successfully subscribing to channel..");
                }
                catch (Throwable t) {
                    throw t;
                }
                finally {
                    if (jedis[0] != null) {
                        if (t2 != null) {
                            try {
                                jedis[0].close();
                            }
                            catch (Throwable t3) {
                                t2.addSuppressed(t3);
                            }
                        }
                        else {
                            jedis[0].close();
                        }
                    }
                }
            }
            catch (Exception exception) {
                System.out.println("[Kirooh] Failed to subscribe to channel..");
                exception.printStackTrace();
            }
        });
    }

    static {
        Kirooh.PARSER = new JsonParser();
    }
}