package org.gradle.caching.ng.test;

import org.openjdk.jmh.infra.*;

import java.io.*;
import java.net.*;
import java.util.*;

public interface HttpRequester extends Closeable {
    void request(List<URI> urls, Blackhole blackhole) throws Exception;
}
