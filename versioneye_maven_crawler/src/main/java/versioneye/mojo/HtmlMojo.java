package versioneye.mojo;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.resolution.ArtifactResult;
import org.htmlcleaner.TagNode;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import com.versioneye.persistence.IPomDao;
import versioneye.service.RabbitMqService;
import versioneye.utils.MavenCentralUtils;

import javax.jms.*;
import java.util.*;

public class HtmlMojo extends SuperMojo {

    static final Logger logger = LogManager.getLogger(HtmlMojo.class.getName());

    protected String split1Pattern = "(?i)href=\"";
    protected String split2Pattern = "\".*";
    protected String startPoint;

    protected final static String QUEUE_NAME = "html_worker";
    protected Connection connection;
    protected IPomDao pomDao;
    protected MavenCentralUtils mavenCentralUtils;
    private Set<String> urls = new HashSet<String>(); // Follow each UEL only once per crawl.


    public void execute() throws MojoExecutionException, MojoFailureException {
        super.execute();
        if (context == null){
            context = new ClassPathXmlApplicationContext("applicationContext.xml");
        }
        pomDao = (IPomDao) context.getBean("pomDao");
        mavenCentralUtils = (MavenCentralUtils) context.getBean("mavenCentralUtils");
    }

    public void crawl() {
        initTheRabbit();
        if (repository.getFollowType().equals("text")){
            split1Pattern = "(?i)href=(?=.*['\"])(.*?)(?=.*['\"])>";
            split2Pattern = "<.*";
        }
        String src = repository.getSrc();
        if (startPoint == null || startPoint.equals("")){
            startPoint = src;
        }
        if (startPoint != null && !startPoint.endsWith("/")){
            startPoint = startPoint + "/";
        }
        follow(startPoint);
        closeTheRabbit();
    }

    public void follow(String currentUrl){
        logger.debug("follow " + currentUrl);
        List<String> links = getLinksFromPage(currentUrl);
        for (String href : links){
            if (href.startsWith(":")){
                href = href.replaceFirst(":", "");
            }

            if (isFollowable(href, currentUrl)){
                String newUrl = createNewUrl(href, currentUrl);
                if (!urls.contains(newUrl)){
                    urls.add(newUrl);
                    follow(newUrl);
                } else {
                    logger.info(" already in cache, skip " + newUrl);
                    continue;
                }
            } else {
                logger.info(" - NOT followable: " + href);
            }

            if (href.endsWith(".pom") && !href.contains("SNAPSHOT")) {
                String newUrl = createNewUrl(href, currentUrl);
                sendPom(newUrl);
            }
        }
    }

    public List<String> getLinksFromPage(String src){
        List<String> links = new ArrayList<String>();
        try{
            String html = httpUtils.getHttpResponse(src);
            String[] firstS = html.split(split1Pattern);

            for (String element: firstS){
                String link = element.replaceAll(split2Pattern, "");
                if (repository.isReplaceWithRepoSrc()){
                    link = link.replaceFirst(repository.getSrc(), "");
                } else {
                    link = link.replaceFirst(src, "");
                }

                if (link.startsWith("#")){
                    link = link.replaceFirst("#", "");
                }

                if (link.endsWith("md5") || link.endsWith("sha1") || link.endsWith("png") || link.endsWith("jar") ||
                        link.endsWith("ico") || link.endsWith("css") || link.startsWith("?") ||
                        link.endsWith("maven-metadata.xml") || link.startsWith("<") || link.contains(".css?") ||
                        link.equals("../"))
                    continue;

                links.add(link);
            }
        } catch (Exception ex) {
            logger.error("ERROR in HtmlMojo.getLinksFromPage(..) " + ex.toString());
            logger.error(ex);
            return new ArrayList<String>();
        }
        return links;
    }

    private boolean isFollowable(String url, String currentUrl){
        boolean goodUrl = url.length() > 1 && url.endsWith("/") &&
                !url.startsWith("Parent Directory") &&
                !url.endsWith("../") &&
                !url.startsWith(".") &&
                !url.startsWith("www.") &&
                !url.startsWith("<") &&
                !url.startsWith("?") &&
                !url.startsWith(")") &&
                !url.startsWith(").sha1") &&
                !url.startsWith("-") &&
                !url.startsWith("-.sha1") &&
                !url.startsWith("IvyPattern") &&
                !url.startsWith("IvyPattern.sha1") &&
                !url.startsWith("LAST_BUILD_OK_release_--scheduler-only") &&
                !url.startsWith("LAST_BUILD_OK_release_--scheduler-only.sha1") &&
                !url.startsWith("archetype-catalog.xml") &&
                !url.startsWith("archetype-catalog.xml.md5") &&
                !url.startsWith("archetype-catalog.xml.sha1") &&
                !url.startsWith("com.everbridge.notification.md5") &&
                !url.startsWith("com.everbridge.notification.sha1") &&
                !url.startsWith("/");
        if (goodUrl && !url.startsWith("http")){
            return true;
        }
        if (goodUrl && url.startsWith(currentUrl)){
            return true;
        }
        return false;
    }

    protected String createNewUrl(String href, String currentUrl){
        String newUrl = "";
        if (href.startsWith("http")){
            newUrl = href;
        } else {
            newUrl = currentUrl + href;
        }
        return newUrl;
    }

    protected void sendPom(String urlToPom){
        try{
            if (pomDao.existsAlready(urlToPom)){
                logger.info("Skip pom, because it was already parsed! " + urlToPom);
                return ;
            }
            String message = repository.getName() + "::" + urlToPom;
            // Create a session.
            Session producerSession = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            // Create a queue named "MyQueue".
            Destination producerDestination = producerSession.createQueue(QUEUE_NAME);

            // Create a producer from the session to the queue.
            MessageProducer producer = producerSession.createProducer(producerDestination);
            producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);

            // Create a message.
            TextMessage producerMessage = producerSession.createTextMessage(message);

            // Send the message.
            producer.send(producerMessage);
            logger.info(" [x] Sent '" + message + "'");
            producer.close();
            producerSession.close();
        } catch (Exception exception) {
            logger.error("urlToPom: " + urlToPom + " - " + exception.toString() );
            logger.error(exception);
        }
    }

    protected void processPom(String urlToPom) {
        try{
            String groupId       = null;
            String artifactId    = null;
            String versionNumber = null;
            String packaging     = null;

            urlToPom = urlToPom.replaceAll("/:", "/");
            TagNode pom = httpUtils.getPageForResource(urlToPom, username, password);
            if (pom != null){
                HashMap<String, String> properties = mavenUrlProcessor.getProperties(pom, null);
                groupId       = mavenUrlProcessor.getGroupId(    pom, properties);
                artifactId    = mavenUrlProcessor.getArtifactId( pom, properties);
                versionNumber = mavenUrlProcessor.getVersion(    pom, properties);
                packaging     = mavenUrlProcessor.getPackaging(  pom, properties);
            } else {
                logger.info(" - TagNode object is null for " + urlToPom);
                Model model   = mavenCentralUtils.fetchModelFromUrl(urlToPom, username, password);
                groupId       = model.getGroupId();
                artifactId    = model.getArtifactId();
                versionNumber = model.getVersion();
                packaging     = model.getPackaging();
            }

            if (groupId == null || artifactId == null || versionNumber == null) {
                logger.info(" - Couldnt fetch GAV from TagNode. Now trying mavenCentralUtils.fetchModelFromUrl for " + urlToPom);
                Model model   = mavenCentralUtils.fetchModelFromUrl(urlToPom, username, password);
                groupId       = model.getGroupId();
                artifactId    = model.getArtifactId();
                versionNumber = model.getVersion();
                packaging     = model.getPackaging();
            }

            if (groupId == null || artifactId == null || versionNumber == null){
                logger.error("ERROR: could not fetch GAV (" + groupId + ":" + artifactId + ":" + versionNumber + ") for " + urlToPom );
                return ;
            }

            boolean existAlreadyLowerCase = productDao.doesVersionExistAlreadyByGA( groupId.toLowerCase(), artifactId.toLowerCase(), versionNumber );
            boolean existAlready          = productDao.doesVersionExistAlreadyByGA( groupId, artifactId, versionNumber );
            if (existAlreadyLowerCase || existAlready){
                logger.info(" --- Exists already: " + groupId + "/" + artifactId + ":" + versionNumber);
                if (pomDao.existsAlready(urlToPom) == false) {
                    pomDao.create(urlToPom);
                }
                return ;
            }

            if (packaging != null && packaging.equalsIgnoreCase("pom")){
                logger.info(" --- Skipp parent pom " + urlToPom);
                return ;
            }

            logger.info(" --- Process: " + groupId + "/" + artifactId + ":" + versionNumber);
            Artifact artifact = getArtifact(groupId + ":" + artifactId + ":pom:" + versionNumber);
            ArtifactResult result = resolveArtifact(artifact);
            resolveDependencies(artifact);
            parseArtifact(result.getArtifact(), null);
            pomDao.create(urlToPom);
        } catch (Exception exception) {
            logger.error("urlToPom: " + urlToPom + " - " + exception.toString());
            logger.error(exception);
        }
    }

    protected void initTheRabbit(){
        try {
            Properties properties = getProperties();
            String rabbitmqAddr = properties.getProperty("rabbitmq_addr");
            String rabbitmqPort = properties.getProperty("rabbitmq_port");

            if (rabbitmqAddr == null || rabbitmqAddr.isEmpty()){
                rabbitmqAddr = System.getenv("RM_PORT_5672_TCP_ADDR");
                rabbitmqPort = System.getenv("RM_PORT_5672_TCP_PORT");
            }
            logger.info("RM_PORT_5672_TCP_ADDR: " + rabbitmqAddr + " RM_PORT_5672_TCP_PORT: " + rabbitmqPort);

            connection = RabbitMqService.getProducerConnection(rabbitmqAddr, new Integer(rabbitmqPort));
        } catch (Exception exception){
            logger.error("ERROR in initTheRabbit - " + exception.toString());
            logger.error(exception);
        }
    }

    protected void closeTheRabbit(){
        try{
            connection.close();
        } catch (Exception exception){
            logger.error("ERROR in closeTheRabbit - " + exception.toString());
            logger.error(exception);
        }
    }

}
