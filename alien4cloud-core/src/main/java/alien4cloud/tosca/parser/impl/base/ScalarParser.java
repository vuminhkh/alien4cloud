package alien4cloud.tosca.parser.impl.base;

import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.nodes.Node;

import alien4cloud.tosca.parser.ParserUtils;
import alien4cloud.tosca.parser.ParsingContextExecution;
import alien4cloud.tosca.parser.mapping.DefaultParser;

/**
 * Very simple scalar parser that just returns the value as string.
 */
@Component
public class ScalarParser extends DefaultParser<String> {

    @Override
    public String parse(Node node, ParsingContextExecution context) {
        return ParserUtils.getScalar(node, context);
    }
}