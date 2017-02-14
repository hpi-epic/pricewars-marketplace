FROM hseeberger/scala-sbt:latest

RUN apt-get update -qq
RUN apt-get install -y postgresql

ENV APP_HOME /marketplace
RUN mkdir $APP_HOME
WORKDIR $APP_HOME

ADD build.sbt $APP_HOME
ADD project/build.properties $APP_HOME/project/
ADD project/plugins.sbt $APP_HOME/project/

RUN sbt update

ADD . $APP_HOME

RUN sbt compile

CMD ["./start-marketplace.sh"]
