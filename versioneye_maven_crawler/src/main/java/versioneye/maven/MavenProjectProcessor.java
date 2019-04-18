package versioneye.maven;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.maven.model.Developer;
import org.apache.maven.model.License;
import org.apache.maven.model.Organization;
import org.apache.maven.project.MavenProject;
import com.versioneye.domain.Dependency;
import com.versioneye.domain.Product;
import com.versioneye.domain.Repository;
import com.versioneye.domain.Version;
import com.versioneye.persistence.IDeveloperDao;
import com.versioneye.persistence.ILicenseDao;
import com.versioneye.persistence.IProductDao;
import com.versioneye.service.ArchiveService;
import com.versioneye.service.DependencyService;
import com.versioneye.service.ProductService;
import com.versioneye.service.VersionLinkService;
import versioneye.utils.MavenUrlUtils;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created with IntelliJ IDEA.
 * User: robertreiz
 * Date: 7/28/13
 * Time: 5:52 PM
 */
public class MavenProjectProcessor {

    private ProductService productService;
    private ArchiveService archiveService;
    private VersionLinkService versionLinkService;
    private DependencyService dependencyService;
    private IProductDao productDao;
    private IDeveloperDao developerDao;
    private ILicenseDao licenseDao;
    private MavenUrlUtils mavenUrlUtils = new MavenUrlUtils();
    private Repository repository;

    static final Logger logger = LogManager.getLogger(MavenProjectProcessor.class.getName());

    public void updateLicense(MavenProject project){
        try{
            Product product = fetchOrCreateProduct(project);
            updateLicenseInfo(product, project);
        } catch (Exception ex) {
            logger.error("ERROR in updateLicense ", ex);
            return ;
        }
    }

    public void updateProject(MavenProject project, Date lastModified) {
        try{
            Product product = fetchOrCreateProduct(project);
            addVersionIfNotExist(product, project, lastModified);

            logger.info("gavLicenses: " + project.getGroupId() + "/" + project.getArtifactId() + ":" + project.getVersion() + " has " + project.getLicenses().size() + " licenses");

            String urlToPom = "";
            String urlToProduct = "";
            if (repository.getName().equals("central") || repository.getName().equalsIgnoreCase("MavenCentral") ){
                urlToPom = mavenUrlUtils.getPomUrl( project.getGroupId(), project.getArtifactId(), project.getVersion() );
                urlToProduct = mavenUrlUtils.getProductUrl( project.getGroupId(), project.getArtifactId() );
            } else {
                urlToPom = mavenUrlUtils.getPomUrl( repository.getSrc(), project.getGroupId(), project.getArtifactId(), project.getVersion() );
                urlToProduct = mavenUrlUtils.getProductUrl( repository.getSrc(), project.getGroupId(), project.getArtifactId() );
            }
            archiveService.createArchivesIfNotExist(product, urlToPom);
            createLinks(product, project, urlToProduct);

            createDependenciesIfNotExist(product, project);
            createDeveloperIfNotExist(product, project);
            updateLicenseInfo(product, project);
        } catch (Exception ex) {
            logger.error("ERROR in updateProject ", ex);
            return ;
        }
    }

    private void addVersionIfNotExist(Product product, MavenProject project, Date lastModified){
        try{
            Version version = new Version();
            version.setVersion(project.getVersion());
            version.setProduct_key( product.getProd_key() );
            if (lastModified != null){
                version.setReleased_at(lastModified);
                setReleasedDateString(version, lastModified);
            }
            boolean new_version = productService.createVersionIfNotExist(product, version, repository);
            if (new_version){
                logger.info("New Artifact: " + product.getProd_key() + " : " + version.getVersion());
            }
        } catch (Exception ex) {
            logger.error("ERROR in addVersionIfNotExist ", ex);
        }
    }

    private void setReleasedDateString(Version version, Date lastModified){
        try{
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            version.setReleased_string(sdf.format(lastModified));
        } catch (Exception ex) {
            logger.error("ERROR in setReleasedDateString ", ex);
        }
    }

    private void createLinks(Product product, MavenProject project, String urlToProduct){
        urlToProduct = checkEndingSlash( urlToProduct );
        String projectUrl = checkEndingSlash( project.getUrl() );
        versionLinkService.createLinkIfNotExist( product.getLanguage(), product.getProd_key(), null, "Link to Repository", urlToProduct);
        versionLinkService.createLinkIfNotExist( product.getLanguage(), product.getProd_key(), product.getVersion(), "URL", projectUrl );
        if (project.getOrganization() != null){
            Organization organization = project.getOrganization();
            String organisationName = organization.getName();
            String organisationUrl  = checkEndingSlash( organization.getUrl() );
            versionLinkService.createLinkIfNotExist( product.getLanguage(), product.getProd_key(), null, organisationName, organisationUrl);
        }
        if (project.getScm() != null && project.getScm().getUrl() != null){
            String scmUrl = checkEndingSlash( project.getScm().getUrl() );
            versionLinkService.createLinkIfNotExist( product.getLanguage(), product.getProd_key(),
                    product.getVersion(), "SCM", scmUrl );
        }
    }


    private void createDependenciesIfNotExist(Product product, MavenProject project){
        if (project.getDependencies() == null || project.getDependencies().isEmpty()){
            return ;
        }

        for (org.apache.maven.model.Dependency dep : project.getDependencies() ){
            createDepIfNotExist(project, product, dep);
        }

        if (project.getDependencyManagement() != null){
            for ( org.apache.maven.model.Dependency dep : project.getDependencyManagement().getDependencies() ){
                createDepIfNotExist(project, product, dep);
            }
        }
    }

    private void createDepIfNotExist(MavenProject project, Product product, org.apache.maven.model.Dependency dep){
        try{
            String key = dep.getGroupId() + "/" + dep.getArtifactId();
            Dependency dependency = new Dependency(product.getLanguage(), product.getProd_key(), project.getVersion(),
                    dep.getArtifactId(), dep.getVersion(), key);
            dependency.setScope(dep.getScope());
            dependency.setGroupId(dep.getGroupId());
            dependency.setArtifactId(dep.getArtifactId());
            dependency.setProdType("Maven2");
            dependencyService.createDependencyIfNotExist(dependency);
            //logger.info(" - new dependency: " + dependency.getGroupId() + "/" + dependency.getArtifactId());
        } catch (Exception ex){
            logger.error("ERROR in createDepIfNotExist ", ex);
        }
    }

    private void createDeveloperIfNotExist(Product product, MavenProject project){
        for ( Developer developer : project.getDevelopers() ){
            String role = "";
            if (developer.getRoles() != null && !developer.getRoles().isEmpty()){
                role = developer.getRoles().get(0);
            }
            com.versioneye.domain.Developer dev = new com.versioneye.domain.Developer(product.getLanguage(), product.getProd_key(),
                    product.getVersion(), developer.getId(), developer.getName(), developer.getEmail(),
                    developer.getUrl(), role, developer.getOrganization(), developer.getOrganizationUrl(),
                    developer.getTimezone());
            if (!developerDao.doesExistAlready(product.getLanguage(), product.getProd_key(), product.getVersion(), developer.getName()))
                developerDao.create(dev);
        }
    }

    private void updateLicenseInfo(Product product, MavenProject project){
        for ( License license : project.getLicenses() ) {
            com.versioneye.domain.License license1 = new com.versioneye.domain.License();
            if (licenseDao.existAlready(product.getLanguage(), product.getProd_key(), product.getVersion(), license.getName(), license.getUrl())){
                logger.info("license "+ license.getName() +" for " + product.getLanguage() + ":" + product.getProd_key() + ":" + product.getVersion() + " exist already");
                return ;
            }
            license1.setName( license.getName() );
            license1.setUrl(license.getUrl());
            license1.setComments(license.getName());
            license1.setDistributions(license.getDistribution());
            license1.setLanguage(product.getLanguage());
            license1.setProd_key(product.getProd_key());
            license1.setVersion(product.getVersion());
            licenseDao.create(license1);
            logger.info("new license " + license.getName() + " for " + product.getLanguage() + ":" + product.getProd_key() + ":" + product.getVersion());
        }
    }

    private Product fetchOrCreateProduct(MavenProject project) {
        Product product = null;
        try {
            product = productDao.getByGA(project.getGroupId(), project.getArtifactId());
            if (product == null){
                product = productDao.getByGA(project.getGroupId().toLowerCase(), project.getArtifactId().toLowerCase());
            }
        } catch (Exception ex){
            logger.error("ERROR in fetchOrCreateProduct ", ex);
        }
        if (product == null){
            product = newProduct( project );
        }
        product.setVersion( project.getVersion() );
        return product;
    }

    private Product newProduct( MavenProject project ){
        Product product = new Product();
        if (repository != null){
            product.addRepository(repository);
            product.setProd_type(repository.getRepoType());
            product.setLanguage(repository.getLanguage());
        } else {
            product.setProd_type("Maven2");
            product.setLanguage("Java");
        }
        product.setGroupId(project.getGroupId().toLowerCase());
        product.setArtifactId(project.getArtifactId().toLowerCase());
        product.setGroupId_orig(project.getGroupId());
        product.setArtifactId_orig(project.getArtifactId());
        product.setProd_key(project.getGroupId().toLowerCase() + "/" + project.getArtifactId().toLowerCase());
        product.setName(project.getArtifactId());
        product.setVersion(project.getVersion());
        product.setDescription( project.getDescription() );
        boolean newProduct = productService.createProductIfNotExist(product, repository);
        if (newProduct){
            logger.info("New product: " + product.getProd_key() + " : " + product.getVersion() );
        }
        return product;
    }

    private String checkEndingSlash(String url){
        if (url == null){
            return null;
        }
        if (url.endsWith("/")){
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    public void setProductService(ProductService productService) {
        this.productService = productService;
    }

    public void setProductDao(IProductDao productDao) {
        this.productDao = productDao;
    }

    public void setArchiveService(ArchiveService archiveService) {
        this.archiveService = archiveService;
    }

    public void setDependencyService(DependencyService dependencyService) {
        this.dependencyService = dependencyService;
    }

    public void setVersionLinkService(VersionLinkService versionLinkService) {
        this.versionLinkService = versionLinkService;
    }

    public void setDeveloperDao(IDeveloperDao developerDao) {
        this.developerDao = developerDao;
    }

    public void setLicenseDao(ILicenseDao licenseDao) {
        this.licenseDao = licenseDao;
    }

    public void setRepository(Repository repository) {
        this.repository = repository;
    }

}
