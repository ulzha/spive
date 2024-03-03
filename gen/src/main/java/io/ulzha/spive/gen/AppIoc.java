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
      String[] parts = typeTag.split("\\.");
      return parts[parts.length - 1];
    }

    public String getImport() {
      String[] parts = typeTag.split(":");
      return "import " + parts[1] + ";";
    }

    public String getAccept() {
      return """
               void accept(final %s event);

               default void accept(final %s event, final EventTime eventTime) {
                 accept(event);
               }
             """
          .formatted(getName(), getName());
      // Any neater type signature possible? Help ensure that exactly one of the two is implemented?
      // TODO default void accept(final Clicc event, final Instant eventInstant) {}
    }

    public String getTypeVarName() {
      String name = getName();
      return name.substring(0, 1).toLowerCase() + name.substring(1) + "Type";
    }

    public String getType() {
      return """
               private static final Type %s =
                   Type.fromTypeTag("%s");
             """
          .formatted(getTypeVarName(), getTypeTag());
      // (protobuf Any, anyone? type_url: "type.googleapis.com/company.entity.Foo")
    }

    public String getEmitIf() {
      return """
               public boolean emitIf(Supplier<Boolean> check, %s payload) {
                 return emitIf(check, %s, payload);
               }

               public boolean emitIf(Supplier<Boolean> check, %s payload, EventTime eventTime) {
                 return emitIf(check, %s, payload, eventTime);
               }
             """
          .formatted(getName(), getTypeVarName(), getName(), getTypeVarName());
    }

    public String getEmitConsequential() {
      return """
               public void emitConsequential(%s payload) {
                 emitConsequential(%s, payload);
               }
             """
          .formatted(getName(), getTypeVarName());
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

  public static record AppDescriptor(
      String mainClass, List<EventDescriptor> events, List<WorkloadDescriptor> workloads) {
    public String getPackage() {
      int iDot = mainClass.lastIndexOf('.');
      return "package " + String.join(".", mainClass.substring(0, iDot), "spive", "gen") + ";";
      // app.gen? app.shell? app.bus? app.scaffold? app.spive.gen?
      // layers, outer spive.sdk.ioc.gen + spive.sdk.ioc.lib, and inner spive.sdk.lib?
    }

    public String getImport() {
      return "import " + mainClass + ";";
    }

    public String getName() {
      String[] parts = mainClass.split("\\.");
      return parts[parts.length - 1];
    }
  }

  private static final STGroup templates = new STGroupDir("spive/gen/", '%', '%');

  static {
    templates.setListener(new ThrowingListener());
  }

  public static void generateAppInstanceCode(AppDescriptor app, String dir) throws IOException {
    final ST st = templates.getInstanceOf("AppInstance");
    st.add("app", app);
    st.add("events", app.events);
    st.add("workloads", app.workloads);

    try (FileWriter writer = new FileWriter(dir + "/" + app.getName() + "Instance.java")) {
      writer.write(st.render());
    }
  }

  public static void generateAppOutputGatewayCode(AppDescriptor app, String dir)
      throws IOException {
    final ST st = templates.getInstanceOf("AppOutputGateway");
    st.add("app", app);
    st.add("events", app.events);

    try (FileWriter writer = new FileWriter(dir + "/" + app.getName() + "OutputGateway.java")) {
      writer.write(st.render());
    }
  }
}
