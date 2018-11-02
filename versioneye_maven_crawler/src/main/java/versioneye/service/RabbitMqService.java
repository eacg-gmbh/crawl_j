package versioneye.service;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.jms.pool.PooledConnectionFactory;

import javax.jms.Connection;
import javax.jms.JMSException;

public class RabbitMqService {

    public static Connection getConnection(String host, int port) throws JMSException {
        // Create a connection factory.
        final ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(System.getenv("MQ_URI"));

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

    private static boolean isQueueInitialized = false;
    private static PooledConnectionFactory pooledConnectionFactory;

    private static void initQueue() throws JMSException {
        if (isQueueInitialized) return;

        // Create a connection factory.
        final ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(System.getenv("MQ_URI"));

        // Pass the username and password.
        String activeMqUsername = System.getenv("RM_PORT_USER");
        String activeMqPassword = System.getenv("RM_PORT_CRED");
        connectionFactory.setUserName(activeMqUsername);
        connectionFactory.setPassword(activeMqPassword);

        // Create a pooled connection factory.
        pooledConnectionFactory = new PooledConnectionFactory();
        pooledConnectionFactory.setConnectionFactory(connectionFactory);
        pooledConnectionFactory.setMaxConnections(10);
        pooledConnectionFactory.start();

        isQueueInitialized = true;
    }

    public static Connection getProducerConnection(String host, int port) throws Exception {
        initQueue();
        // Establish a connection for the producer.
        final Connection producerConnection = pooledConnectionFactory.createConnection();
        producerConnection.start();
        return producerConnection;
    }


}
