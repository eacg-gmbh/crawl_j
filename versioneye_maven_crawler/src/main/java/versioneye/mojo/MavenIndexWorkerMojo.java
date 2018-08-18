package versioneye.mojo;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.QueueingConsumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import com.versioneye.domain.MavenRepository;
import com.versioneye.domain.Repository;
import versioneye.service.RabbitMqService;

import java.util.Date;
import java.util.Properties;


@Mojo( name = "maven_index_worker", defaultPhase = LifecyclePhase.PROCESS_SOURCES )
public class MavenIndexWorkerMojo extends AetherMojo {

    static final Logger logger = LogManager.getLogger(MavenIndexWorkerMojo.class.getName());

    private final static String QUEUE_NAME = "maven_index_worker";

    public void execute() throws MojoExecutionException, MojoFailureException {
        try{
            super.execute();

            username = null;
            password = null;

            Connection connection = initConnection();
            Channel channel = connection.createChannel();

            channel.queueDeclare(QUEUE_NAME, true, false, false, null);
            logger.info(" [*] Waiting for messages. To exit press CTRL+C");

            QueueingConsumer consumer = new QueueingConsumer(channel);
            channel.basicQos(1);
            channel.basicConsume(QUEUE_NAME, false, consumer);

            System.out.println("[*] waiting for messages. To exit press CTRL+C");

            while(true) {
                QueueingConsumer.Delivery delivery = consumer.nextDelivery();
                if(delivery != null) {
                    String message = new String(delivery.getBody());
                    logger.info(" . ");
                    logger.info(" [x] Received '" + message + "'");
                    processMessage( message );
                    logger.info(" [x] Job done for '" + message + "'");
                    channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                }
            }
        } catch( Exception exception ){
            logger.error("MavenIndexWorkerMojo failed: " + exception.getMessage());
            logger.error(exception);
            throw new MojoExecutionException("Oh no! Something went wrong. Get in touch with the VersionEye guys and give them feedback.", exception);
        }
    }

    private void processMessage(String message){
        try{
            String[] sps = message.split("::");

            String repoName     = sps[0].toLowerCase();
            String repoUrl      = sps[1];
            String gav          = sps[2];

            fetchUserAndPassword();

            String language = "Java";
            if (repoName.toLowerCase().equals("clojars") || repoUrl.equals("https://clojars.org/repo") ) {
                language = "Clojure";
            }

            Repository repo = repositoryUtils.convertRepository(repoName, repoUrl, language);
            setRepository(repo, true);

            MavenRepository mrepo = repositoryUtils.convertMavenRepository(repoName, repoUrl, language);
            setMavenRepository(mrepo, true);

            Date releasedAt = getReleasedDate(sps);

            processGav(gav, releasedAt);
        } catch (Exception exception) {
            logger.error("MavenIndexWorkerMojo[processMessage] failed: " + exception.getMessage());
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
        String msg = "Connect to RabbitMQ: " + rabbitmqAddr + ":" + rabbitmqPort;
        logger.info(msg);
        System.out.println(msg);
        return RabbitMqService.getConnection(rabbitmqAddr, new Integer(rabbitmqPort));
    }


    private Date getReleasedDate(String[] sps){
        Date releasedAt = null;
        try{
            String lastModified = sps[3];
            if (lastModified != null && !lastModified.trim().isEmpty()){
                releasedAt = new Date(new Long(lastModified));
            }
        } catch (Exception ex) {
            logger.error("MavenIndexWorkerMojo[getReleasedDate] failed: " + ex.getMessage());
            logger.error(ex);
        }
        return releasedAt;
    }

}
