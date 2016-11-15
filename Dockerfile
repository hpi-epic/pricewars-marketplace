FROM hseeberger/scala-sbt:latest
ADD . /marketplace
WORKDIR /marketplace
RUN apt-get install -y postgres
RUN service postgres start
RUN createdb marketplace
CMD sbt ~container:start
