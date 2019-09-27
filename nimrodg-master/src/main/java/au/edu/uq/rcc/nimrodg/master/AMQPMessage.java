package au.edu.uq.rcc.nimrodg.master;

import au.edu.uq.rcc.nimrodg.agent.messages.AgentMessage;
import com.rabbitmq.client.AMQP;

import javax.mail.internet.ContentType;
import java.nio.charset.Charset;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public class AMQPMessage {
    public final byte[] body;
    public final AMQP.BasicProperties basicProperties;
    public final UUID messageId;
    public final ContentType contentType;
    public final Charset charset;
    public final Optional<Instant> sentAt;
    public final AgentMessage message;

    public AMQPMessage(byte[] body, AMQP.BasicProperties basicProperties, UUID messageId, ContentType contentType, Charset charset, Optional<Instant> sentAt, AgentMessage message) {
        this.body = body;
        this.basicProperties = basicProperties;
        this.messageId = messageId;
        this.contentType = contentType;
        this.charset = charset;
        this.sentAt = sentAt;
        this.message = message;
    }
}
