package com.github.andremoniy.pipedstreams.multithread;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestPipedStreamsInConfinedThreadPool {

    @Test
    void three_threads_should_not_be_blocked_in_a_confined_thread_pool_with_2_threads_for_a_small_block_of_data() throws InterruptedException, ExecutionException, TimeoutException {
        // Given
        final ExecutorService executorService = Executors.newFixedThreadPool(2);
        final byte[] source = new byte[1024];

        // When
        final CompletableFuture<Void> completableFuture = createAndSubmitJob(executorService, source);

        // Then
        assertNull(completableFuture.get(5, TimeUnit.SECONDS));
    }

    @Test
    void three_threads_should_be_blocked_in_a_confined_thread_pool_with_2_threads_for_a_large_block_of_data() {
        // Given
        final ExecutorService executorService = Executors.newFixedThreadPool(2);
        final byte[] source = new byte[1025];

        // When
        final CompletableFuture<Void> completableFuture = createAndSubmitJob(executorService, source);

        // Then
        assertThrows(TimeoutException.class, () -> completableFuture.get(5, TimeUnit.SECONDS));
    }

    @Test
    void three_threads_should_not_be_blocked_in_a_confined_thread_pool_with_3_threads_for_a_large_block_of_data() throws InterruptedException, ExecutionException, TimeoutException {
        // Given
        final ExecutorService executorService = Executors.newFixedThreadPool(3);
        final byte[] source = new byte[1025];

        // When
        final CompletableFuture<Void> completableFuture = createAndSubmitJob(executorService, source);

        // Then
        completableFuture.get(5, TimeUnit.SECONDS);
    }

    private CompletableFuture<Void> createAndSubmitJob(ExecutorService executorService, byte[] source) {
       return CompletableFuture.runAsync(() -> {
            try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(source);
                 PipedOutputStream pipedOutputStream = new PipedOutputStream();
                 PipedInputStream pipedInputStream = new PipedInputStream(pipedOutputStream);
                 ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {

                final CompletableFuture<Void> uploadCompletableFuture = CompletableFuture.runAsync(() -> {
                    try {
                        byteArrayInputStream.transferTo(pipedOutputStream);
                        pipedOutputStream.close();
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                }, executorService);

                final CompletableFuture<Void> downloadCompletableFuture = CompletableFuture.runAsync(() -> {
                    try {
                        pipedInputStream.transferTo(byteArrayOutputStream);
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                }, executorService);

                try {
                    CompletableFuture.allOf(uploadCompletableFuture, downloadCompletableFuture).get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    throw new IllegalStateException(e);
                }

            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }, executorService);
    }

}
