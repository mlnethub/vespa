// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.configmodelview;

import com.yahoo.tensor.Tensor;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Config model view of an imported machine-learned model.
 *
 * @author bratseth
 */
public interface ImportedMlModel {

    String name();
    String source();
    Optional<String> inputTypeSpec(String input);
    Map<String, String> smallConstants();
    Map<String, Tensor> smallConstantValues();
    Map<String, String> largeConstants();
    Map<String, Tensor> largeConstantValues();
    Map<String, String> functions();
    List<ImportedMlFunction> outputExpressions();

}
