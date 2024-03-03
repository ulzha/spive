package io.ulzha.spive.gen;

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import org.stringtemplate.v4.*;
import org.stringtemplate.v4.misc.STMessage;

public class AppIoc {
  /** WTF jump through hoops because at StringTemplate they don't do exceptions */
  private static class PrintingListener implements STErrorListener {
    @Override
    public void compileTimeError(STMessage msg) {
      System.err.println(msg);
    }

    @Override
    public void runTimeError(STMessage msg) {
      System.err.println(msg);
    }

    @Override
    public void IOError(STMessage msg) {
      System.err.println(msg);
    }

    @Override
    public void internalError(STMessage msg) {
      System.err.println(msg);
    }
  }

  public static record EventConfig(String name, String type) {}
  ;

  public static record WorkloadConfig(String name) {}
  ;

  public static record AppConfig(
      String name, List<EventConfig> events, List<WorkloadConfig> workloads) {}
  ;

  // private static final STGroup templates = new STGroupDir("spive/gen");
  private static final STGroup templates = new STGroupFile("spive/gen/AppInstance.stg");

  public static void generateAppOutputGatewayCode(AppConfig config, String dir) {
    final ST st = templates.getInstanceOf("AppOutputGateway");
    st.add("event", config.events);
  }

  public static void generateAppInstanceCode(AppConfig config, String dir) throws IOException {
    templates.setListener(new PrintingListener());
    System.err.println("Come on: " + templates.show());
    final ST st = templates.getInstanceOf("body");
    st.add("workloads", config.workloads);

    try (FileWriter writer = new FileWriter(dir + "/" + config.name + "Instance.java")) {
      writer.write(st.render());
    }
  }
}
