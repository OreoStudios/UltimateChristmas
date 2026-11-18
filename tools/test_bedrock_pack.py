"""Asset tests; these do not substitute for a Bedrock client animation test."""
import io
import json
from pathlib import Path
import unittest
import uuid
import zipfile

import generate_bedrock_pack as compiler


class BedrockPackTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.files, cls.mapping, cls.display_yaml, cls.report = compiler.build()

    def test_v2_definitions_match_every_live_item_model_exactly(self):
        expected = set()
        for path in compiler.RIGS.glob('*.json'):
            rig = json.loads(path.read_text(encoding='utf-8'))
            expected.update(b['model'] for b in rig['bones'] if b.get('model'))
        self.assertEqual(2, self.mapping['format_version'])
        self.assertEqual(['minecraft:paper'], list(self.mapping['items']))
        definitions = self.mapping['items']['minecraft:paper']
        self.assertEqual(expected, {d['model'] for d in definitions})
        self.assertEqual(202, len(definitions))
        self.assertEqual(len(definitions), len({d['bedrock_identifier'] for d in definitions}))
        for definition in definitions:
            self.assertEqual('definition', definition['type'])
            self.assertNotIn('custom_model_data', definition)
            self.assertNotIn('components', definition)
            self.assertFalse(definition['bedrock_identifier'].startswith('minecraft:'))
            self.assertIn('item-identifier: "' + definition['bedrock_identifier'] + '"', self.display_yaml)

    def test_every_geometry_texture_icon_and_render_controller_resolves(self):
        atlas = json.loads(self.files['textures/item_texture.json'])['texture_data']
        geometries = {}
        attachables = {}
        for name, content in self.files.items():
            if name.endswith('.geo.json'):
                for geometry in json.loads(content)['minecraft:geometry']:
                    geometries[geometry['description']['identifier']] = geometry
            elif name.endswith('.attachable.json'):
                attach = json.loads(content)['minecraft:attachable']['description']
                attachables[attach['identifier']] = attach
        cube_count = 0
        for definition in self.mapping['items']['minecraft:paper']:
            attach = attachables[definition['bedrock_identifier']]
            geometry = geometries[attach['geometry']['default']]
            self.assertEqual((256, 256), compiler.png_size(self.files[attach['textures']['default'] + '.png']))
            icon_path = atlas[definition['bedrock_options']['icon']]['textures']
            self.assertIn(icon_path + '.png', self.files)
            self.assertEqual(["controller.render.ultimatechristmas.rig_part"], attach['render_controllers'])
            self.assertIn('geyser:item_display', attach['item'][definition['bedrock_identifier']])
            self.assertEqual(['geyser', 'geyser_x', 'geyser_y', 'geyser_z', 'uc_item'],
                             [b['name'] for b in geometry['bones']])
            self.assertEqual([0, 180, 0], geometry['bones'][-1]['rotation'])
            for cube in geometry['bones'][-1]['cubes']:
                cube_count += 1
                self.assertTrue(all(s > 0 for s in cube['size']))
                for face in cube['uv'].values():
                    for axis in range(2):
                        self.assertTrue(0 <= face['uv'][axis] <= 256)
                        self.assertTrue(0 <= face['uv'][axis] + face['uv_size'][axis] <= 256)
        self.assertEqual(343, cube_count)

    def test_geometry_and_signed_uvs_match_actual_blockbench_export(self):
        # Exported by Blockbench MCP at 16-unit UV resolution, without centering.
        reference = json.loads((Path(__file__).parent / 'fixtures/blockbench_grinch_head.geo.json')
                               .read_text(encoding='utf-8'))['minecraft:geometry'][0]['bones'][0]['cubes']
        actual = json.loads(self.files['models/entity/ultimatechristmas/boss_grinch_head.geo.json'])
        cubes = actual['minecraft:geometry'][0]['bones'][-1]['cubes']
        self.assertEqual(len(reference), len(cubes))
        for wanted, got in zip(reference, cubes):
            for axis, offset in enumerate([8, 0, -8]):
                self.assertAlmostEqual(wanted['origin'][axis] + offset, got['origin'][axis])
                self.assertAlmostEqual(wanted['size'][axis], got['size'][axis])
            self.assertEqual(set(wanted['uv']), set(got['uv']))
            for direction in wanted['uv']:
                for key in ('uv', 'uv_size'):
                    for axis in range(2):
                        self.assertAlmostEqual(wanted['uv'][direction][key][axis] * 16,
                                               got['uv'][direction][key][axis])

    def test_face_rotation_and_disabled_faces_survive_conversion(self):
        element = {'from': [7, 6, 5], 'to': [9, 10, 11], 'faces': {
            'north': {'texture': '#0', 'uv': [3, 2, 1, 4], 'rotation': 90}}}
        converted = compiler.convert_cube(element, 256, 128)
        self.assertEqual([-1, 6, -3], converted['origin'])
        self.assertEqual(['north'], list(converted['uv']))
        self.assertEqual({'uv': [48, 16], 'uv_size': [-32, 16], 'uv_rotation': 90}, converted['uv']['north'])
        element['rotation'] = {'axis': 'y', 'angle': 22.5}
        with self.assertRaises(ValueError):
            compiler.convert_cube(element, 256, 128)

    def test_mcpack_is_deterministic_has_root_manifest_and_no_java_files(self):
        first = compiler.zip_bytes(self.files)
        self.assertEqual(first, compiler.zip_bytes(dict(reversed(list(self.files.items())))))
        with zipfile.ZipFile(io.BytesIO(first)) as archive:
            self.assertIsNone(archive.testzip())
            manifest = json.loads(archive.read('manifest.json'))
            self.assertEqual(2, manifest['format_version'])
            self.assertNotEqual(uuid.UUID(manifest['header']['uuid']),
                                uuid.UUID(manifest['modules'][0]['uuid']))
            self.assertEqual('resources', manifest['modules'][0]['type'])
            self.assertFalse(any(n.startswith('assets/') or '\\' in n for n in archive.namelist()))
            self.assertFalse(any('custom_mappings' in n for n in archive.namelist()))
        self.assertEqual(43, sum(len(m['server_animations']) for m in self.report['models']))
        self.assertTrue(self.report['requires_display_entity_extension'])
        self.assertFalse(self.report['bedrock_client_visual_tested'])


if __name__ == '__main__':
    unittest.main()
