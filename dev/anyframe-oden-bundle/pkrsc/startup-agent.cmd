set ARGS=-Xmx32m -XX:NewRatio=1 -XX:MinHeapFreeRatio=20 -XX:MaxHeapFreeRatio=25
java %ARGS% -jar oden-2.3.0.jar conf/agent.ini