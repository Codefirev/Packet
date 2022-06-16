package rip.kirooh.handler;

import java.lang.annotation.*;

@Target({ ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface IncomingPacketHandler {
}
