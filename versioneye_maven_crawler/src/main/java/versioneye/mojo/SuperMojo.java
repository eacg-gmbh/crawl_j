package versioneye.mojo;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.*;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.*;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.filter.DependencyFilterUtils;
import org.eclipse.aether.util.repository.AuthenticationBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import com.versioneye.domain.GlobalSetting;
import com.versioneye.domain.MavenRepository;
import com.versioneye.domain.Repository;
import versioneye.maven.MavenPomProcessor;
import versioneye.maven.MavenProjectProcessor;
import versioneye.maven.MavenUrlProcessor;
import com.versioneye.persistence.IGlobalSettingDao;
import com.versioneye.persistence.IMavenRepostoryDao;
import com.versioneye.persistence.IProductDao;
import versioneye.service.TimeStampService;
import com.versioneye.utils.HttpUtils;
import versioneye.utils.PropertiesUtils;
import versioneye.utils.RepositoryUtils;

import java.io.File;
import java.util.Date;
import java.util.List;
import java.util.Properties;

/**
 * The Mother of all Mojos!
 */
public abstract class SuperMojo extends AbstractMojo {

    static final Logger logger = LogManager.getLogger(SuperMojo.class.getName());

    protected String username = "admin";
    protected String password = "admin";

    @Component
    protected RepositorySystem system;

    @Component
    protected ProjectBuilder projectBuilder;

    @Parameter(defaultValue = "${localRepository}" )
    protected ArtifactRepository localRepository;

    @Parameter( defaultValue="${project}" )
    protected MavenProject project;

    @Component
    protected RepositorySystem repoSystem;

    @Parameter( defaultValue="${repositorySystemSession}" )
    protected RepositorySystemSession session;

    @Parameter( defaultValue = "${project.remoteProjectRepositories}")
    protected List<RemoteRepository> repos;

    @Parameter( defaultValue = "${basedir}", property = "basedir", required = true)
    protected File projectDirectory;

    protected MavenRepository mavenRepository;
    protected Repository repository;
    protected MavenProjectProcessor mavenProjectProcessor;
    protected MavenPomProcessor mavenPomProcessor;
    protected MavenUrlProcessor mavenUrlProcessor;
    protected IMavenRepostoryDao mavenRepositoryDao;
    protected IProductDao productDao;
    protected IGlobalSettingDao globalSettingDao;
    protected RepositoryUtils repositoryUtils = new RepositoryUtils();
    protected HttpUtils httpUtils;
    protected TimeStampService timeStampService;
    protected ApplicationContext context;


    public void execute() throws MojoExecutionException, MojoFailureException {
        try{
            context = new ClassPathXmlApplicationContext("applicationContext.xml");
            mavenProjectProcessor = (MavenProjectProcessor) context.getBean("mavenProjectProcessor");
            mavenPomProcessor = (MavenPomProcessor) context.getBean("mavenPomProcessor");
            mavenUrlProcessor = (MavenUrlProcessor) context.getBean("mavenUrlProcessor");
            mavenRepositoryDao = (IMavenRepostoryDao) context.getBean("mavenRepositoryDao");
            productDao = (IProductDao) context.getBean("productDao");
            globalSettingDao = (IGlobalSettingDao) context.getBean("globalSettingDao");
            httpUtils = (HttpUtils) context.getBean("httpUtils");
            timeStampService = (TimeStampService) context.getBean("timeStampService");
        } catch (Exception ex){
            logger.error(ex);
        }
    }


    protected void parseArtifact(Artifact artifactInfo, Date releasedAt) throws Exception {
        if ( releasedAt == null && repository != null &&
           ( repository.getName().equalsIgnoreCase("MavenCentral") ||
             repository.getName().equalsIgnoreCase("central")   ) ) {
            releasedAt = timeStampService.getTimeStampFor(artifactInfo.getGroupId(), artifactInfo.getArtifactId(), artifactInfo.getVersion());
        }
        MavenProject projectModel = buildProjectModel( artifactInfo );
        if (projectModel == null){
            logger.error("projectModel is null. Try 2nd way!");
            mavenPomProcessor.updateNode(artifactInfo.getGroupId(), artifactInfo.getArtifactId(), artifactInfo.getVersion(), releasedAt);
        } else {
            if (projectModel.getVersion().startsWith("${")){
                projectModel.setVersion(artifactInfo.getVersion());
                logger.info("------ 42 is not the answer! ------");
                logger.info("------ Upsi! ProjectModel doesn't seems to be complete! The Maven God is mad today!");
                logger.info("------ projectModels key is " + projectModel.getGroupId() + "/" + projectModel.getArtifactId() + " : " + projectModel.getVersion());
                logger.info("------ 42 is not the answer! ------");
            }
            mavenProjectProcessor.updateProject( projectModel, releasedAt );
        }
    }


    protected ArtifactResult resolveArtifact(Artifact artifact) throws MojoExecutionException, MojoFailureException {
        if (artifact == null)
            return null;
        try {
            ArtifactRequest request = new ArtifactRequest();
            request.setArtifact( artifact );
            request.setRepositories( repos );
            return repoSystem.resolveArtifact( session, request );
        } catch ( ArtifactResolutionException e ) {
            logger.error("resolveArtifact failed for " + artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion() + " with repos: " + repos);
            throw new MojoExecutionException( e.getMessage(), e );
        }
    }

    public ArtifactDescriptorResult resolveDependencies(Artifact artifact) throws MojoExecutionException, MojoFailureException {
        if (artifact == null)
            return null;
        try {
            //logger.info("Resolving dependencies for " + artifact + " from " + repos);
            ArtifactDescriptorRequest descriptorRequest = new ArtifactDescriptorRequest();
            descriptorRequest.setArtifact( artifact );
            descriptorRequest.setRepositories( repos );

            ArtifactDescriptorResult descriptorResult = repoSystem.readArtifactDescriptor( session, descriptorRequest );

            return descriptorResult;
        } catch (Exception ex) {
            logger.error("resolveDependencies failed for " +
                    artifact.getGroupId() + ":" +
                    artifact.getArtifactId() + ":" +
                    artifact.getVersion() + " with repos: " +
                    repos + " ex: " +
                    ex.getMessage());
            throw new MojoFailureException( ex.getMessage(), ex );
        }
    }

    private void resolveTransitiveDependencies(Artifact artifact){
        DependencyFilter classpathFlter = DependencyFilterUtils.classpathFilter(JavaScopes.COMPILE);
        CollectRequest collectRequest = new CollectRequest();
        collectRequest.setRoot(new Dependency(artifact, JavaScopes.COMPILE));
        collectRequest.setRepositories( repos );
        DependencyRequest dependencyRequest = new DependencyRequest(collectRequest, classpathFlter);

        try {
            List<ArtifactResult> artifactResults = repoSystem.resolveDependencies(session, dependencyRequest).getArtifactResults();
            for (ArtifactResult artifactResult : artifactResults) {
                logger.info(" - " + artifactResult.getArtifact() + " resolved to " + artifactResult.getArtifact().getFile());
            }
        } catch (DependencyResolutionException e1) {
            e1.printStackTrace();
        }
    }

    protected MavenProject buildProjectModel(Artifact artifactInfo) throws Exception {
        return getProject(artifactInfo);
    }

    protected MavenProject buildProjectModelFor(String groupId, String artifactId, String versionNumber) throws Exception {
        try {
            Artifact artifact = getArtifact(groupId + ":" + artifactId + ":" + versionNumber);
            return getProject(artifact);
        } catch (Exception ex) {
            throw new MojoFailureException( ex.getMessage(), ex );
        }
    }

    protected MavenProject getProject(Artifact artifact) throws MojoExecutionException, MojoFailureException {
        try {
            //logger.info("build project from file: " + artifact.getFile());

            ProjectBuildingRequest projectBuildingRequest = new DefaultProjectBuildingRequest();
            projectBuildingRequest.setLocalRepository(localRepository);
            projectBuildingRequest.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL);
            projectBuildingRequest.setProcessPlugins(false);
            projectBuildingRequest.setSystemProperties(System.getProperties());
            projectBuildingRequest.setRepositoryMerging(ProjectBuildingRequest.RepositoryMerging.REQUEST_DOMINANT);
            ProjectBuildingResult pbr = projectBuilder.build(artifact.getFile(), projectBuildingRequest);
            MavenProject project = pbr.getProject();

            return project;
        } catch (Exception ex) {
            logger.error("getProject failed for " + artifact.getGroupId() + ":" + artifact.getArtifactId() + "" + artifact.getVersion() + " with repos: " + repos);
            throw new MojoFailureException( ex.getMessage(), ex );
        }
    }

    protected Artifact getArtifact(String artifactCoords) throws MojoExecutionException, MojoFailureException {
        try {
            return new DefaultArtifact( artifactCoords );
        } catch ( IllegalArgumentException e ) {
            throw new MojoFailureException( e.getMessage(), e );
        }
    }


    protected Properties getProperties() throws Exception {
        PropertiesUtils propertiesUtils = new PropertiesUtils();
        String propFile = projectDirectory + "/src/main/resources/settings.properties";

        File file = new File(propFile);
        if (!file.exists())
            throw new MojoExecutionException(propFile + " is missing!");

        return propertiesUtils.readProperties(propFile);
    }


    protected void fetchUserAndPassword(){
        String env = System.getenv("RAILS_ENV");
        try{
            GlobalSetting gs = globalSettingDao.getBy(env, "mvn_repo_1_user");
            if (gs != null)
                username = gs.getValue();
            gs = globalSettingDao.getBy(env, "mvn_repo_1_password");
            if (gs != null)
                password = gs.getValue();
        } catch( Exception ex){
            ex.printStackTrace();
            username = "admin";
            password = "password";
        }
    }


    protected void setRepository(String repoName){
        if (context == null){
            context = new ClassPathXmlApplicationContext("applicationContext.xml");
        }
        Repository rep = (Repository) context.getBean(repoName);
        setRepository(rep, true);

        MavenRepository mp = mavenRepositoryDao.findByName(repoName);
        setMavenRepository(mp, true);
    }

    protected void setRepository(Repository repository, boolean withAuth){
        if (repository == null){
            return ;
        }
        this.repository = repository;
        if (withAuth){
            this.repository.setUsername(username);
            this.repository.setPassword(password);
        }
        mavenProjectProcessor.setRepository(this.repository);
        mavenPomProcessor.setRepository(this.repository);
    }

    protected void setMavenRepository(MavenRepository mp, boolean withAuth){
        if (mp == null)
            return;

        mavenRepository = mp;
        if (withAuth) {
            mavenRepository.setUsername(username);
            mavenRepository.setPassword(password);
        }
        addRepo(mavenRepository);
    }

    protected void addRepo(MavenRepository repository){
        if (repository == null){
            return ;
        }
        for (RemoteRepository rr : repos ){
            if (rr.getId().equals(repository.getName())){
                return ;
            }
        }

        RemoteRepository.Builder builder = new RemoteRepository.Builder(repository.getName(), "default", repository.getUrl());
        if (repository.getUsername() != null && !repository.getUsername().isEmpty() && repository.getPassword() != null && !repository.getPassword().isEmpty()){
            AuthenticationBuilder authBuilder =  new AuthenticationBuilder();
            authBuilder.addUsername(repository.getUsername());
            authBuilder.addPassword(repository.getPassword());
            builder.setAuthentication(authBuilder.build());
        }

        RemoteRepository remoteRepository = builder.build();
        repos.add(remoteRepository);
        logger.info("There are " + repos.size() + " remote repositories in the list");
    }

    protected void addAllRepos() {
        List<MavenRepository> repositories = mavenRepositoryDao.loadAll();
        for (MavenRepository repository : repositories) {
            if (repository.getName().equals("central"))
                continue;
            if (repository.getUrl().equals("http://download.java.net/maven/2/"))
                continue;
            RemoteRepository.Builder builder = new RemoteRepository.Builder(repository.getName(), "default", repository.getUrl());
            RemoteRepository remoteRepository = builder.build();
            repos.add(remoteRepository);
        }
        logger.info("There are " + repos.size() + " remote repositories in the list");
    }



}
