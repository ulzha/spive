package io.ulzha.spive.app.events;

import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;

// Should manage in SpiveInventory land? KTLO does not need type creation...
public record CreateType(
    // partition key... (Unlikely we ever need more types than fit in one instance)
    UUID typeId,
    String name,
    /* TODO @Nullable order/partitioning model */
    /* TODO artifactId...? Inline serdes for various programming languages, to be proliferated in code generation? Or CreateGateway may upload the artifact(s) I guess, that's where serde knowledge resides? */
    /* Unclear how portability across languages is encoded. TODO */
    // may be unset, indicates creation of a primitive type
    // field type tags must already be known (in core or created with CreateType)
    @Nullable Map<String, String> fieldTypeTags) {}
