#!/usr/bin/env groovy

@Grapes([
    @Grab(group='org.apache.tika', module='tika-core', version='1.18')
])

import org.apache.tika.Tika

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.LongAdder
import java.util.concurrent.CountDownLatch

import static groovy.io.FileType.FILES

def cli = new CliBuilder(header: 'MIME Type Reporter', usage:'./mimeReporter <directoryToScan>', width: 100)

def cliOptions = cli.parse(args)

if (cliOptions.help || cliOptions.arguments().size() != 1) {
  cli.usage()
  System.exit(0)
}

def results = new ConcurrentHashMap()
def fileAbsolutePaths = []
def tika = new Tika()
def executor = Executors.newWorkStealingPool()

new File(cliOptions.arguments().first()).eachFileRecurse(FILES) { file ->
  fileAbsolutePaths << file.absolutePath
}

def latch = new CountDownLatch(fileAbsolutePaths.size())

println "Processing ${fileAbsolutePaths.size()} files..."

fileAbsolutePaths.each { filePath ->
  executor.submit {
    try {
      results.computeIfAbsent(tika.detect(new File(filePath)), { k -> new LongAdder() }).increment()
    } finally {
      latch.countDown()
    }
  }
}

latch.await()
executor.shutdown()

def formatOutput = '%-40s occurred %d times%n' 

results.each { contentType, counter ->
  System.out.format(formatOutput, contentType, counter.intValue())
}
