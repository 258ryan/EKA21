package com.github.eka2l1.belle;

import com.github.eka2l1.config.ProfileModel;
import com.github.eka2l1.settings.KeyMapper;

/** What a Symbian app starts with when it is opened like an installed app: no touch keyboard, gamepad keys, any orientation. */
public final class BelleDefaults {
    private BelleDefaults() {
    }

    public static void apply(ProfileModel p) {
        p.showKeyboard = false;
        p.touchInput = true;
        p.orientation = 1; // follow the device
        p.screenScaleType = 1; // fit, keep the aspect ratio
        p.screenGravity = 1;
        p.screenBackgroundColor = 0x000000;
        if (p.keyMappings == null) {
            p.keyMappings = KeyMapper.getDefaultKeyMap();
        }
    }
}
