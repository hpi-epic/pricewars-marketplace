FROM hseeberger/scala-sbt:8u141-jdk_2.12.3_0.13.16

ENV APP_HOME /marketplace
RUN mkdir $APP_HOME
WORKDIR $APP_HOME

ADD . $APP_HOME

RUN sbt update
RUN sbt compile

CMD ["./start-marketplace.sh"]
