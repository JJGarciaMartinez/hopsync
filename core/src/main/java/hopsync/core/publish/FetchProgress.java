package hopsync.core.publish;

/**
 * How far reading a pack back from its repository has got.
 *
 * @param done  files downloaded so far
 * @param total files to download, or 0 while it is not known yet
 */
public record FetchProgress(int done, int total) {
}
