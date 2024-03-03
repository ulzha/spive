package io.ulzha.spive.gen;

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import org.stringtemplate.v4.*;
import org.stringtemplate.v4.misc.STMessage;

public class AppIoc {
  /** WTF jump through hoops because at StringTemplate they don't do exceptions */
  private static class ThrowingListener implements STErrorListener {
    @Override
    public void compileTimeError(STMessage msg) {
      throw new RuntimeException(msg.toString());
    }

    @Override
    public void runTimeError(STMessage msg) {
      throw new RuntimeException(msg.toString());
    }

    @Override
    public void IOError(STMessage msg) {
      throw new RuntimeException(msg.toString());
    }

    @Override
    public void internalError(STMessage msg) {
      throw new RuntimeException(msg.toString());
    }
  }

  public static record EventDescriptor(String typeTag) {
    public String getName() {
      String[] parts = typeTag.split(".");
      return parts[parts.length - 1];
    }
    public String getImport() {
      String[] parts = typeTag.split(":");
      return "import " + parts[1] + ";";
    }
    public String getTypeTag() {
      return typeTag;
    }
  }

  public static record WorkloadDescriptor(String name) {
    public String getNew() {
      return "app.new " + name + "()";
    }
  }

  public static record AppConfig(
      String name, List<EventDescriptor> events, List<WorkloadDescriptor> workloads) {}

  // private static final STGroup templates = new STGroupDir("spive/gen");
  private static final STGroup templates = new STGroupFile("spive/gen/AppInstance.stg", '%', '%');

  public static void generateAppOutputGatewayCode(AppConfig config, String dir) {
    final ST st = templates.getInstanceOf("AppOutputGateway");
    st.add("event", config.events);
  }

  public static void generateAppInstanceCode(AppConfig config, String dir) throws IOException {
    templates.setListener(new ThrowingListener());
    System.err.println("Come on: " + templates.show());
    final ST st = templates.getInstanceOf("body");
    st.add("events", config.events);
    st.add("workloads", config.workloads);

    try (FileWriter writer = new FileWriter(dir + "/" + config.name + "Instance.java")) {
      writer.write(st.render());
    }
  }
}
