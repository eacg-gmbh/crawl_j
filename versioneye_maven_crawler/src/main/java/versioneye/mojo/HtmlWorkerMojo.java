package versioneye.mojo;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import versioneye.service.RabbitMqService;
import versioneye.utils.QueueingConsumer;

import javax.jms.*;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

@Mojo( name = "html_worker", defaultPhase = LifecyclePhase.PROCESS_SOURCES )
public class HtmlWorkerMojo extends HtmlMojo {

    static final Logger logger = LogManager.getLogger(HtmlWorkerMojo.class.getName());

    private final static String QUEUE_NAME = "html_worker";

    public void execute() throws MojoExecutionException, MojoFailureException {
        try{
            super.execute();

            username = null;
            password = null;

            // Create a session.
            Session consumerSession = initConnection().createSession(false, Session.CLIENT_ACKNOWLEDGE);

            // Create a queue named "MyQueue".
            Destination consumerDestination = consumerSession.createQueue(QUEUE_NAME);

            // Create a message consumer from the session to the queue.
            MessageConsumer consumer = consumerSession.createConsumer(consumerDestination);

            System.out.println("[*] waiting for messages. To exit press CTRL+C");

            while(true) {
                try {

                    // Begin to wait for messages.
                    Message consumerMessage = consumer.receive(100000);

                    if(consumerMessage != null) {
                        String message = ((TextMessage) consumerMessage).getText();
                        logger.info(" . ");
                        logger.info(" [x] Received '" + message + "'");
                        processMessage( message );
                        logger.info(" [x] Job done for '" + message + "'");
                        consumerMessage.acknowledge();
                    }
                } catch (JMSException e) {
                    closeTheRabbit();
                    initTheRabbit();
                    logger.info("JMSException: Re-Init ActiveMQ");
                    logger.info(e);
                    try {
                        TimeUnit.SECONDS.sleep(2);
                    } catch(InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        } catch( Exception exception ){
            exception.printStackTrace();
            logger.error(exception);
            throw new MojoExecutionException("Oh no! Something went wrong. Get in touch with the VersionEye guys and give them feedback.", exception);
        }
    }

    private void processMessage(String message){
        try{
            String[] sps = message.split("::");
            String repoName = sps[0];
            String pomUrl = sps[1];

            setRepository( repoName );

            processPom( pomUrl );
        } catch (Exception exception) {
            exception.printStackTrace();
            logger.error(exception);
        }
    }

    private Connection initConnection() throws Exception {
        String rabbitmqAddr = System.getenv("RM_PORT_5672_TCP_ADDR");
        String rabbitmqPort = System.getenv("RM_PORT_5672_TCP_PORT");
        if (rabbitmqAddr == null || rabbitmqAddr.isEmpty() || rabbitmqPort == null || rabbitmqPort.isEmpty()){
            Properties properties = getProperties();
            rabbitmqAddr = properties.getProperty("rabbitmq_addr");
            rabbitmqPort = properties.getProperty("rabbitmq_port");
        }
        return RabbitMqService.getConnection(rabbitmqAddr, new Integer(rabbitmqPort));
    }

}
