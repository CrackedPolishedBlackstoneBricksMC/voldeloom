mojang piston meta 1.4.7 client hash 53ed4b9d5c358ecfff2d8b846b4427b888287028

* downloading from the url https://launcher.mojang.com/v1/objects/53ed4b9d5c358ecfff2d8b846b4427b888287028/client.jar with a web browser and checking with powershell, agrees

but actual on disk 1.4.7 client jar hash f170af59f2c7bda6d455c8d6a13540d6f826a680

* Powershell also agrees tbh?

either the jank ass downloader is corrupting something, something is overwriting the file, or what

# figured it out

if you set `Accept-Encoding: gzip`, the server will happily respond with a gzipped data stream that *inflates to a completely different jar file*. like the jars contain the same contents as each other

More testing w/ `curl` confirms that its probably something to do with GzipInputStream