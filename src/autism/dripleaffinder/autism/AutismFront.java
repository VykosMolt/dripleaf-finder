package dripleaffinder.autism;

import autismclient.api.SimpleAddon;
import dripleaffinder.DripleafClient;

public final class AutismFront extends SimpleAddon {
    public AutismFront() {
        super(3, "dripleaffinder");
    }

    @Override
    protected void initialize() {
        registerModule(new SplicedDripleaf(DripleafClient.menu()));
    }
}
