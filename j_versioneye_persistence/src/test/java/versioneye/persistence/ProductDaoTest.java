package versioneye.persistence;

import org.bson.types.ObjectId;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import versioneye.domain.*;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertTrue;

/**
 * Created by IntelliJ IDEA.
 * User: reiz
 * Date: 1/1/12
 * Time: 7:05 PM
 *
 */
public class ProductDaoTest {

    private static String KEY = "/group/arti5";
    private static String LANGUAGE = "Java";
    private final static String REPOSITORY_SRC = "http://ibiblio.org";
    private final static String VERSION_LINK = "http://version.de";
    private Product product;
    private IProductDao productDao;
    private DomainFactory domainFactory;
    private static ApplicationContext context;

    private Repository mavRepo;
    private Repository gemRepo;

    @Test
    public void init(){
        context = new ClassPathXmlApplicationContext("applicationContext.xml");
        productDao = (IProductDao) context.getBean("productDao");
        domainFactory = (DomainFactory) context.getBean("domainFactory");
    }

    @BeforeClass
    public void before(){
        KEY += String.valueOf(Math.random());
    }

    @Test(dependsOnMethods = {"init"})
    public void doCreate1() throws Exception{
        productDao.dropAllProducts();

        mavRepo = new Repository();
        mavRepo.setName("mav2");
        mavRepo.setSrc(REPOSITORY_SRC);
        mavRepo.setRepoType("Maven2");

        gemRepo = new Repository();
        gemRepo.setName("rubygems");
        gemRepo.setSrc("http://rubygems.org");
        gemRepo.setRepoType("RubyGems");

        product = new Product();
        product.setName("Name");
        product.setLanguage(LANGUAGE);
        product.setProd_key(KEY);
        product.setGroupId("group1");
        product.setArtifactId("arti1");
        product.setLink("www.server.de/group/arti/");
        product.addRepository(mavRepo);
        productDao.create(product);
    }

    @Test(dependsOnMethods = {"doCreate1"})
    public void doExistAlready(){
        Assert.assertTrue( productDao.existAlready(LANGUAGE, KEY) );
    }

    @Test(dependsOnMethods = {"doExistAlready"})
    public void getByKey() throws Exception{
        Product product = productDao.getByKey(LANGUAGE, KEY);
        assertNotNull(product);
        assertEquals(product.getName(), "Name");
        assertEquals(product.getProd_key(), KEY);
        assertEquals(product.getRepositories().size(), 1);
        assertFalse( productDao.doesVersionExistAlready(LANGUAGE, KEY, "1.0") );
    }

    @Test(dependsOnMethods = {"getByKey"})
    public void getUniqueFollowedJavaIds() throws Exception{
        List<ObjectId> ids = productDao.getUniqueFollowedJavaIds();
        assertNotNull(ids);
        assertTrue(ids.isEmpty());

        Product javaProd = new Product();
        javaProd.setName("Name");
        javaProd.setLanguage("Java");
        javaProd.setProd_key("org.hibernate/hibernate");
        javaProd.setGroupId("org.hibernate");
        javaProd.setArtifactId("hibernate");
        javaProd.setLink("www.server.de/group/arti/");
        javaProd.addRepository(mavRepo);
        javaProd.setFollowers(1);
        productDao.create(javaProd);

        ids = productDao.getUniqueFollowedJavaIds();
        assertNotNull(ids);
        assertTrue(!ids.isEmpty());
        assertEquals(ids.size(), 1);

        Product gem = new Product();
        gem.setName("Name");
        gem.setLanguage("Ruby");
        gem.setProd_key("activerecord");
        gem.setLink("www.server.de/group/arti/");
        gem.addRepository(gemRepo);
        gem.setFollowers(1);
        productDao.create(gem);

        // Now there are more products with followers in db, but still only 1 java prod.
        ids = productDao.getUniqueFollowedJavaIds();
        assertNotNull(ids);
        assertTrue(!ids.isEmpty());
        assertEquals(ids.size(), 1);

        productDao.remove(gem.getDBObject());
        productDao.remove(javaProd.getDBObject());
    }

    @Test(dependsOnMethods = {"getUniqueFollowedJavaIds"} )
    public void doUpdate() throws Exception {
        String link = "www.server.de/group/arti/a";
        Version version = new Version();
        version.setProduct_key(KEY);
        version.setVersion("1.1");
        version.setLink(link);
        productDao.updateVersionInfosInProduct(product.getLanguage(), KEY, version);
        Product productUpdated = productDao.getByKey(LANGUAGE, KEY);
        assertEquals(productUpdated.getVersion(), "1.1");
        assertEquals(productUpdated.getVersion_link(), link);
    }

    @Test(dependsOnMethods = {"doUpdate"})
    public void addVersion() throws Exception {
        Version version = new Version();
        version.setVersion("1.0");
        version.setLink(VERSION_LINK);
        version.setProduct_key(KEY);
        productDao.addNewVersion(LANGUAGE, KEY, version);
        assertTrue(productDao.doesVersionExistAlready(LANGUAGE, KEY, "1.0"));
        Product product = productDao.getByKey(LANGUAGE, KEY);
        assertNotNull(product);
        assertEquals(product.getVersions().size(), 1);

        productDao.updateVersionInfosInProduct(product.getLanguage(), product.getProd_key(), version);

        assertTrue(productDao.doesVersionExistAlready(LANGUAGE, KEY, "1.0"));

        Product prod = productDao.getByKey(LANGUAGE, KEY);
        Assert.assertNotNull(prod);
        assertEquals(prod.getVersions().size(), 1);
    }

    @Test(dependsOnMethods = {"addVersion"})
    public void doesVersionExistAlreadyByGA(){
        Assert.assertTrue( productDao.doesVersionExistAlreadyByGA(product.getGroupId(), product.getArtifactId(), "1.0") );
    }

    @Test(dependsOnMethods = {"addVersion"})
    public void addRepo(){
        RepoType repoType = new RepoType();
        repoType.setName("Maven2");

        Repository repository = new Repository();
        repository.setName("ibiblio");
        repository.setSrc(REPOSITORY_SRC + "12");
        repository.setRepoType("Maven2");
        Assert.assertFalse(productDao.doesRepositoryExistAlready(product.getLanguage(), KEY, repository.getSrc()));
        productDao.addNewRepository(product.getLanguage(), KEY, repository);
        try{
            Thread.sleep(200);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        Assert.assertTrue( productDao.doesRepositoryExistAlready(product.getLanguage(), KEY, repository.getSrc()) );
    }

}
