package versioneye.service;

import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.Connection;
import javax.jms.JMSException;

public class RabbitMqService {

    public static Connection getConnection(String host, int port) throws JMSException {
        // Create a connection factory.
        final ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory("amqps://b-b4b5a14b-3023-4fbf-bdc2-9fbdf1690f16-1.mq.eu-central-1.amazonaws.com:5671");

        // Pass the username and password.
        String activeMqUsername = System.getenv("RM_PORT_USER");
        String activeMqPassword = System.getenv("RM_PORT_CRED");
        connectionFactory.setUserName(activeMqUsername);
        connectionFactory.setPassword(activeMqPassword);

        // Establish a connection for the producer.
        final Connection consumerConnection = connectionFactory.createConnection();
        consumerConnection.start();

        return consumerConnection;
    }

}
