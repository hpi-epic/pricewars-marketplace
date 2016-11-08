FROM hseeberger/scala-sbt:latest
ADD . /marketplace
WORKDIR /marketplace
CMD sbt ~container:start