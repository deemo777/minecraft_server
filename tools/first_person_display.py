"""Shared first-person offset, applied once to freshly loaded source models."""


def adjust_first_person(model):
    for hand in ('firstperson_righthand', 'firstperson_lefthand'):
        pose = model['display'][hand]
        x, y, z = pose.get('translation', [0, 0, 0])
        # Minecraft mirrors X for the left hand, so positive X moves either hand outward.
        pose['translation'] = [round(x + 3, 9), round(y - 4, 9), z]
    return model
