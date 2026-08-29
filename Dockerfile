# Requires build.properties to exist in the build context (copy it from
# build.properties.example and fill in real values first). Its contents -- including DB
# credentials -- get baked into this image via Maven resource filtering, so treat the built
# image the same way you'd treat that file: don't push it anywhere outside your own network.
FROM maven:3.9.16-eclipse-temurin-8-noble

WORKDIR /app

# Cache dependency resolution in its own layer so source-only changes don't re-download the world.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline || true

COPY . .

# Same buildnumber-maven-plugin workaround as CI: it tries to git-log/git-pull the SCM URL in
# pom.xml, which has no business succeeding (or being reachable) during an image build.
RUN mvn -B clean package war:exploded \
      -Dmaven.buildNumber.doCheck=false \
      -Dmaven.buildNumber.doUpdate=false

# war:exploded doesn't touch jetty-maven-plugin's own dependencies (its bundled JSP/JSTL support
# among them) since it never runs that plugin. Resolve those into the local repo now, at build
# time, so the offline `jetty:run` at container start isn't the first time they're needed.
RUN mvn -B dependency:resolve-plugins

RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# /data holds the sqlite DB, seeded from the repo's pre-loaded pyx.sqlite (card data already in
# it). build.properties must point hibernate.url at jdbc:sqlite:/data/pyx.sqlite for this to be
# the file actually used. Mounting a volume directly onto a single file only works if that file
# already exists on the host; mounting it onto this directory instead lets Docker auto-populate a
# fresh named volume from the seed copy below on first run.
RUN mkdir -p /data && cp pyx.sqlite /data/pyx.sqlite
VOLUME ["/data"]

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s \
    CMD curl -fs http://localhost:8080/ || exit 1

# -o: offline, so a restart can't hang or fail on a network/repo hiccup -- everything needed was
# already resolved during the image build above. Still re-compiles and re-runs jetty:run's own
# lifecycle on every start (this plugin doesn't separate "build" from "serve"), so start isn't
# instant, but it's a lot faster than the full build above since nothing is re-downloaded or
# re-exploded.
CMD ["mvn", "-B", "-o", "jetty:run", \
     "-Dmaven.buildNumber.doCheck=false", "-Dmaven.buildNumber.doUpdate=false"]
