package versioneye.utils;

import com.versioneye.utils.HttpUtils;
import com.versioneye.utils.LogUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.maven.model.Model;
import org.codehaus.jackson.map.ObjectMapper;
import com.versioneye.domain.Crawle;
import com.versioneye.domain.Repository;
import versioneye.dto.Document;
import versioneye.dto.ResponseJson;
import versioneye.maven.MavenUrlProcessor;
import versioneye.maven.PomReader;
import com.versioneye.persistence.IProductDao;

import java.io.Reader;

/**
 * Created with IntelliJ IDEA.
 * User: robertreiz
 * Date: 7/27/13
 * Time: 8:30 PM
 */
public class MavenCentralUtils {

    static final Logger logger = LogManager.getLogger(MavenCentralUtils.class.getName());

    public static final String LINK_FILE = "http://search.maven.org/remotecontent?filepath=";
    private MavenUrlProcessor mavenUrlProcessor;
    private LogUtils logUtils;
    private HttpUtils httpUtils;
    private IProductDao productDao;
    private Crawle crawle;
    private Repository repository;
    private MavenUrlUtils mavenUrlUtils = new MavenUrlUtils();

    public void crawleArtifact(String groupId, String artifactId){
        String urlToProduct = mavenUrlUtils.getProductUrl(groupId, artifactId);
        String jsonUrl = mavenUrlUtils.getProductJsonUrl(groupId, artifactId);
        String url = jsonUrl.toString();
        try{
            Reader resultReader = httpUtils.getResultReader( url );
            ObjectMapper mapper = new ObjectMapper();
            ResponseJson resp = mapper.readValue(resultReader, ResponseJson.class);
            resultReader.close();
            for (Document doc: resp.getResponse().getDocs()){
                if (doc.getA() == null || doc.getG() == null || doc.getV() == null)
                    continue;
                if (productDao.doesVersionExistAlreadyByGA(groupId, artifactId, doc.getV()) )
                    continue;
                String urlToVersion = mavenUrlUtils.getVersionUrl( groupId, artifactId, doc.getV() );
                String urlToPom = mavenUrlUtils.getPomUrl( groupId, artifactId, doc.getV() );
                mavenUrlProcessor.updateNode(urlToPom, urlToVersion, urlToProduct, getRepository(), crawle);
            }
        } catch (Exception ex) {
            logUtils.addError("error in CrawlerMavenDefaultJson.crawleArtifact( "+groupId+", "+artifactId+" )", ex.toString(), crawle);
        }
    }

    public Model fetchModel(String groupId, String artifactId, String version) {
        try {
            String urlToPom = mavenUrlUtils.getPomUrl( groupId, artifactId, version );
            Reader reader   = httpUtils.getResultReader( urlToPom );
            return PomReader.readSinglePom(reader);
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }

    public Model fetchModelFromUrl(String urlToPom, String username, String password) {
        try {
            Reader reader = httpUtils.getResultReader( urlToPom, username, password );
            return PomReader.readSinglePom(reader);
        } catch (Exception ex) {
            logger.error("ERROR in fetchModelFromUrl: " + urlToPom + " - " + ex.toString());
            logger.error(ex);
            return null;
        }
    }

    public void setMavenUrlProcessor(MavenUrlProcessor mavenUrlProcessor) {
        this.mavenUrlProcessor = mavenUrlProcessor;
    }

    public Crawle getCrawle() {
        return crawle;
    }

    public void setCrawle(Crawle crawle) {
        this.crawle = crawle;
    }

    public Repository getRepository() {
        return repository;
    }

    public void setRepository(Repository repository) {
        this.repository = repository;
    }

    public void setHttpUtils(HttpUtils httpUtils) {
        this.httpUtils = httpUtils;
    }

    public void setLogUtils(LogUtils logUtils) {
        this.logUtils = logUtils;
    }

    public void setProductDao(IProductDao productDao) {
        this.productDao = productDao;
    }
}
