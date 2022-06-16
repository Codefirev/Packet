package rip.kirooh.handler;

public class PacketExceptionHandler
{
    public void onException(final Exception e) {
        System.out.println("Failed to send packet");
        e.printStackTrace();
    }
}
