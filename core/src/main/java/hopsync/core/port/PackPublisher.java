package hopsync.core.port;

import hopsync.core.pack.PackSource;
import hopsync.core.publish.PublishRequest;
import hopsync.core.publish.PublishStep;
import hopsync.core.util.CancellationToken;

import java.io.IOException;
import java.util.function.Consumer;

/** Where created packs are published so friends can follow them by link (a GitHub repository and its release). */
public interface PackPublisher {

    /**
     * Publishes the pack and waits until its manifest is out.
     *
     * @return where it is published, with the request's content hash
     */
    PackSource.Publication publish(PublishRequest request, Consumer<PublishStep> progress, CancellationToken cancel)
        throws IOException;
}
