# Piped streams multithread 
## This is a small demonstration of a Piped Streams issue with a confined thread pool with explanation why it happens and how to solve the issue.

Let us consider the following scenario. We have 3 tasks intended to run separate threads conducting the following actions:

+ a "download" job, which writes a stream into a `PipedOutputStream` instance;
+ an "upload" job, which reads from a `PipedInputStream` and sends this data to another consumer;
+ a wrapper job which starts the download and upload jobs.

If we have a single `ThreadPool` for all jobs execution, then it is crucial to envisage a proper size of this thread pool.

Let us consider in details what happens here. First we submit a wrapper job (the 3rd in the list) into the thread pool. It internally submits two new tasks (#1 and #2). The order is crucial here, for this example let us consider that the "download" job is submitted first.

If the size of the entire source stream is less or equal than `1024` bytes, it is enough to have `2` threads in the pool to execute the described flow of 3 tasks. 
This is demonstrated by the test [`three_threads_should_not_be_blocked_in_a_confined_thread_pool_with_2_threads_for_a_small_block_of_data`](https://github.com/Andremoniy/pipedstreamsmultithread/blob/master/src/test/java/com/github/andremoniy/pipedstreams/multithread/TestPipedStreamsInConfinedThreadPool.java#L23).

However, when the size of the source data exceeds `1024` bytes, then having the thread pool confined by `2` threads becomes an issue. 
[`three_threads_should_be_blocked_in_a_confined_thread_pool_with_2_threads_for_a_large_block_of_data`](https://github.com/Andremoniy/pipedstreamsmultithread/blob/master/src/test/java/com/github/andremoniy/pipedstreams/multithread/TestPipedStreamsInConfinedThreadPool.java#L36) shows that we encounter a `TimeoutException` in this case (and in the worse case we can end-up with a dead-lock situation).

Why? This is mainly because of the buffer inside the `PipedInputStream` which is `1024` by default:
```
private static final int DEFAULT_PIPE_SIZE = 1024;
```

The piped outpustream populates the buffer until it fully filled. However because we have only 2 threads in the pool, the 2rd job with the piped input stream has not been yet executed. It means that nobody reads the data from the buffer. The output stream becomes blocked because of the filled buffer, the input stream is not even started, so we came to a kind of a dead-lock. The wrapper job awaits finishing of execution of both "download" and "upload" jobs, the "download" job is blocked because the "upload" job has not been even started, so if there is a waiting timeout for the "wrapper" job, it will fill fail with a timeout exception.

When the amount of data in the source is less than the buffer size, we do not encounter this issue, thus the output stream is gracefully closed, the "download" job is terminated and the "upload" job is started. In this boundary-case scenario it is enough to have 2 threads in a pool.

![](https://github.com/Andremoniy/pipedstreamsmultithread/blob/master/PipedStreams.png)

Having a proper minimum number of threads in a pool (`3` in this case) perfectly solves the issue: 
[`three_threads_should_not_be_blocked_in_a_confined_thread_pool_with_3_threads_for_a_large_block_of_data`](https://github.com/Andremoniy/pipedstreamsmultithread/blob/master/src/test/java/com/github/andremoniy/pipedstreams/multithread/TestPipedStreamsInConfinedThreadPool.java#L49)
