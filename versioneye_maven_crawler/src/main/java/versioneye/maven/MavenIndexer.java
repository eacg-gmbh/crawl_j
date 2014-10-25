package versioneye.maven;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.maven.index.*;
import org.apache.maven.index.context.IndexCreator;
import org.apache.maven.index.context.IndexUtils;
import org.apache.maven.index.context.IndexingContext;
import org.apache.maven.index.expr.SourcedSearchExpression;
import org.apache.maven.index.updater.*;
import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.events.TransferEvent;
import org.apache.maven.wagon.events.TransferListener;
import org.apache.maven.wagon.observers.AbstractTransferListener;
import org.apache.maven.wagon.providers.http.HttpWagon;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.sonatype.aether.version.InvalidVersionSpecificationException;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class MavenIndexer {

    private final PlexusContainer plexusContainer;
    private final Indexer indexer;
    private final IndexUpdater indexUpdater;
    private final Wagon httpWagon;
    private IndexingContext centralContext;


    public MavenIndexer() throws PlexusContainerException, ComponentLookupException {
        this.plexusContainer = new DefaultPlexusContainer();

        // lookup the indexer components from plexus
        this.indexer      = plexusContainer.lookup( Indexer.class );
        this.indexUpdater = plexusContainer.lookup( IndexUpdater.class );

        // lookup wagon used to remotely fetch index
        this.httpWagon    = plexusContainer.lookup( Wagon.class, "http" );
        Properties p = new Properties();
        p.setProperty("User-Agent", "mojo/nb-repository-plugin");
        HttpWagon httpWagon_ = (HttpWagon) httpWagon;
        httpWagon_.setHttpHeaders(p);
    }

    public void initCentralContext(String repo, String centraCache, String centralIndex) throws IOException, ComponentLookupException, InvalidVersionSpecificationException {
        if (repo == null){
            repo = "http://repo.maven.apache.org/maven2"; // http://repo1.maven.org/maven2 //
        }
        File centralLocalCache = new File( centraCache );
        File centralIndexDir   = new File( centralIndex );

        List<IndexCreator> indexers = new ArrayList<IndexCreator>();
        indexers.add( plexusContainer.lookup( IndexCreator.class, "min" ) );
        indexers.add( plexusContainer.lookup( IndexCreator.class, "jarContent" ) );
        indexers.add( plexusContainer.lookup( IndexCreator.class, "maven-plugin" ) );

        centralContext = indexer.createIndexingContext( "central-context", "central", centralLocalCache,
                centralIndexDir, repo, null, true, true, indexers );
    }

    public void closeIndexer() throws IOException {
        indexer.closeIndexingContext( centralContext, false );
    }

    /*
     * Update the index (incremental update will happen if this is not 1st run and files are not deleted)
     * This whole block below should not be executed on every app start, but rather controlled by some configuration
     * since this block will always emit at least one HTTP GET. Central indexes are updated once a week, but
     * other index sources might have different index publishing frequency.
     * Preferred frequency is once a week.
     */
    public void updateIndex() throws IOException, ComponentLookupException, InvalidVersionSpecificationException {
        System.out.println( "Updating Index..." );
        System.out.println( "This might take a while on first run, so please be patient!" );
        // Create ResourceFetcher implementation to be used with IndexUpdateRequest
        // Here, we use Wagon based one as shorthand, but all we need is a ResourceFetcher implementation
        TransferListener listener = new AbstractTransferListener() {
            public void transferStarted( TransferEvent transferEvent ) {
                System.out.print( "  Downloading " + transferEvent.getResource().getName() );
            }
            public void transferProgress( TransferEvent transferEvent, byte[] buffer, int length ){ }
            public void transferCompleted( TransferEvent transferEvent ) {
                System.out.println( " - Done" );
            }
        };
        ResourceFetcher resourceFetcher     = new WagonHelper.WagonFetcher( httpWagon, listener, null, null );
        Date centralContextCurrentTimestamp = centralContext.getTimestamp();
        IndexUpdateRequest updateRequest    = new IndexUpdateRequest( centralContext, resourceFetcher );
        IndexUpdateResult updateResult      = indexUpdater.fetchAndUpdateIndex( updateRequest );
        if ( updateResult.isFullUpdate() ) {
            System.out.println( "Full update happened!" );
        }
        else if ( updateResult.getTimestamp().equals( centralContextCurrentTimestamp ) ) {
            System.out.println( "No update needed, index is up to date!" );
        }
        else {
            System.out.println( "Incremental update happened, change covered " + centralContextCurrentTimestamp
                    + " - " + updateResult.getTimestamp() + " period." );
        }
    }

    public IteratorSearchResponse executeGroupArtifactSearch(String group, String artifact, String version) throws IOException, ComponentLookupException, InvalidVersionSpecificationException {
        final Query groupIdQ     = indexer.constructQuery( MAVEN.GROUP_ID,    new SourcedSearchExpression( group ) );
        final BooleanQuery query = new BooleanQuery();
        query.add( groupIdQ   , Occur.MUST );

        if (artifact != null && !artifact.trim().isEmpty()){
            final Query artifactIdQ  = indexer.constructQuery( MAVEN.ARTIFACT_ID, new SourcedSearchExpression( artifact ) );
            query.add( artifactIdQ, Occur.MUST );
        }

        if (version != null && !version.trim().isEmpty()){
            final Query versionQ  = indexer.constructQuery( MAVEN.VERSION, new SourcedSearchExpression( version ) );
            query.add( versionQ, Occur.MUST );
        }

//        we want "jar" artifacts only
        query.add( indexer.constructQuery( MAVEN.PACKAGING, new SourcedSearchExpression( "jar" ) ), Occur.MUST );

        // we want main artifacts only (no classifier)
        // Note: this below is unfinished API, needs fixing
//        query.add( indexer.constructQuery( MAVEN.CLASSIFIER, new SourcedSearchExpression( Field.NOT_PRESENT ) ), Occur.MUST_NOT );

        IteratorSearchRequest  request  = new IteratorSearchRequest( query, Collections.singletonList( centralContext ), null );
        IteratorSearchResponse response = indexer.searchIterator( request );
        return response;
    }

    public void walkThroughIndex() throws IOException {
        final IndexSearcher searcher = centralContext.acquireIndexSearcher();
        try {
            final IndexReader ir = searcher.getIndexReader();
            for ( int i = 0; i < ir.maxDoc(); i++ ) {
                if ( !ir.isDeleted( i ) ) {
                    final Document doc = ir.document( i );
                    final ArtifactInfo ai = IndexUtils.constructArtifactInfo( doc, centralContext );

                    System.out.println( ai.groupId + ":" + ai.artifactId + ":" + ai.version + ":" + ai.classifier + "." + ai.fextension + " (sha1=" + ai.sha1 + ")" );
                }
            }
        } finally {
            centralContext.releaseIndexSearcher( searcher );
        }
    }

    public IndexingContext getCentralContext(){
        return centralContext;
    }


}
