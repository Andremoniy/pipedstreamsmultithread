# pipestream smultithread
A small demonstration of Piped Streams issue with a confined thread pool.

Let us consider a simple case. We have 3 threads conducting the following actions:

+ a "download" job, which writes a stream into a `PipedOutputStream` instance
+ an "upload" job, which reads from a `PipedInputStream` and sends this data to another consumer
+ a wrapper job which starts the download and upload jobs

If we have a single `ThreadPool` for all jobs execution, then it is crucial to envisage a proper size of this thread pool.

If the entire source stream is less or equal than `1024` bytes, it is enough to have `2` threads in a pool. 
It is demonstrated by the test `TestPipeStreamsInConfinedThreadPool#three_threads_should_not_be_blocked_in_a_confined_thread_pool_with_2_threads_for_a_small_block_of_data`.

However, when the size of the source data exceeds `1024` bytes, by having a thread pool confined by `2` threads becoming an issue. 
`TestPipeStreamsInConfinedThreadPool#three_threads_should_be_blocked_in_a_confined_thread_pool_with_2_threads_for_a_large_block_of_data` shows that we encounter a `TimeoutException` in this case (and in the worse case we can end-up with a dead-lock situation).

Why? This is because of the buffer inside PipedInputStream which is `1024` by default:
```
private static final int DEFAULT_PIPE_SIZE = 1024;
```

This does not mean of course that one can just adjust the buffer size. Whatever the buffer size is, we should always keep in mind, that this buffer will prevent the output stream from closing. When the amount of data in the source is less than the buffer size, we do not encounter this issue, thus the output stream is gracefully closed, the "download" job is terminated and the "upload" job is started. In this boundary-case scenario it is enough to have 2 threads in a pool.

Having a proper minimum number of threads in a pool (`3` in this case) perfectly solves the issue: 
`TestPipeStreamsInConfinedThreadPool#three_threads_should_not_be_blocked_in_a_confined_thread_pool_with_3_threads_for_a_large_block_of_data`
