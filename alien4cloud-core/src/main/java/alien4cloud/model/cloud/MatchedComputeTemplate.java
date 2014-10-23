package alien4cloud.model.cloud;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuppressWarnings("PMD.UnusedPrivateField")
public class MatchedComputeTemplate {

    /**
     * The Alien compute template
     */
    private ComputeTemplate computeTemplate;

    /**
     * The PaaS resource id
     */
    private String paaSResourceId;
}
