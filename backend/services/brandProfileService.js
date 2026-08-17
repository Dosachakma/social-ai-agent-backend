const db = require('../db/pool');

// In-memory store for mock / offline fallback when database is not configured
const memoryStore = new Map();

/**
 * Service for tenant-isolated Brand Profile CRUD operations.
 */
class BrandProfileService {
  /**
   * List all brand profiles in the workspace.
   */
  async getBrandProfiles(workspaceId) {
    if (!workspaceId) throw new Error('Workspace ID is required');

    if (!db.isConfigured()) {
      const list = [];
      for (const profile of memoryStore.values()) {
        if (profile.workspaceId === workspaceId) {
          list.push({ ...profile });
        }
      }
      return list.sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
    }

    const queryText = `
      SELECT 
        id,
        workspace_id AS "workspaceId",
        name,
        name AS "brandName",
        industry,
        target_audience AS "targetAudience",
        tone_of_voice AS "toneOfVoice",
        primary_language AS "primaryLanguage",
        secondary_language AS "secondaryLanguage",
        writing_style AS "writingStyle",
        preferred_cta AS "preferredCta",
        preferred_hashtags AS "preferredHashtags",
        words_to_avoid AS "wordsToAvoid",
        products_services AS "productsServices",
        website,
        contact_info AS "contactInfo",
        keywords,
        brand_colors AS "brandColors",
        guidelines,
        created_at AS "createdAt",
        updated_at AS "updatedAt"
      FROM brand_profiles
      WHERE workspace_id = $1
      ORDER BY created_at DESC;
    `;

    const result = await db.query(queryText, [workspaceId]);
    return result.rows;
  }

  /**
   * Get single brand profile by ID in the workspace.
   */
  async getBrandProfileById(workspaceId, id) {
    if (!workspaceId || !id) return null;

    if (!db.isConfigured()) {
      const profile = memoryStore.get(id);
      if (profile && profile.workspaceId === workspaceId) {
        return { ...profile };
      }
      return null;
    }

    const queryText = `
      SELECT 
        id,
        workspace_id AS "workspaceId",
        name,
        name AS "brandName",
        industry,
        target_audience AS "targetAudience",
        tone_of_voice AS "toneOfVoice",
        primary_language AS "primaryLanguage",
        secondary_language AS "secondaryLanguage",
        writing_style AS "writingStyle",
        preferred_cta AS "preferredCta",
        preferred_hashtags AS "preferredHashtags",
        words_to_avoid AS "wordsToAvoid",
        products_services AS "productsServices",
        website,
        contact_info AS "contactInfo",
        keywords,
        brand_colors AS "brandColors",
        guidelines,
        created_at AS "createdAt",
        updated_at AS "updatedAt"
      FROM brand_profiles
      WHERE workspace_id = $1 AND id = $2;
    `;

    const result = await db.query(queryText, [workspaceId, id]);
    return result.rows[0] || null;
  }

  /**
   * Create a new brand profile strictly in the authenticated workspace.
   */
  async createBrandProfile(workspaceId, data) {
    if (!workspaceId) throw new Error('Workspace ID is required');

    const name = data.name || data.brandName;
    const industry = data.industry || '';
    const targetAudience = data.targetAudience || data.target_audience || '';
    const toneOfVoice = data.toneOfVoice || data.brandTone || data.tone_of_voice || 'PROFESSIONAL';
    const primaryLanguage = data.primaryLanguage || 'ENGLISH';
    const secondaryLanguage = data.secondaryLanguage || null;
    const writingStyle = data.writingStyle || '';
    const preferredCta = data.preferredCta || '';
    const preferredHashtags = data.preferredHashtags || '';
    const wordsToAvoid = data.wordsToAvoid || '';
    const productsServices = data.productsServices || '';
    const website = data.website || '';
    const contactInfo = data.contactInfo || '';
    const keywords = data.keywords || [];
    const brandColors = data.brandColors || [];
    const guidelines = data.guidelines || data.businessDescription || '';

    if (!db.isConfigured()) {
      const id = data.id || 'b1a2c3d4-e5f6-4a1b-8c2d-' + Date.now().toString(16).padStart(12, '0');
      const item = {
        id,
        workspaceId,
        name,
        brandName: name,
        industry,
        targetAudience,
        toneOfVoice,
        primaryLanguage,
        secondaryLanguage,
        writingStyle,
        preferredCta,
        preferredHashtags,
        wordsToAvoid,
        productsServices,
        website,
        contactInfo,
        keywords,
        brandColors,
        guidelines,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString()
      };
      memoryStore.set(id, item);
      return { ...item };
    }

    const queryText = `
      INSERT INTO brand_profiles (
        workspace_id,
        name,
        industry,
        target_audience,
        tone_of_voice,
        primary_language,
        secondary_language,
        writing_style,
        preferred_cta,
        preferred_hashtags,
        words_to_avoid,
        products_services,
        website,
        contact_info,
        keywords,
        brand_colors,
        guidelines,
        created_at,
        updated_at
      ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15::jsonb, $16::jsonb, $17, NOW(), NOW())
      RETURNING 
        id,
        workspace_id AS "workspaceId",
        name,
        name AS "brandName",
        industry,
        target_audience AS "targetAudience",
        tone_of_voice AS "toneOfVoice",
        primary_language AS "primaryLanguage",
        secondary_language AS "secondaryLanguage",
        writing_style AS "writingStyle",
        preferred_cta AS "preferredCta",
        preferred_hashtags AS "preferredHashtags",
        words_to_avoid AS "wordsToAvoid",
        products_services AS "productsServices",
        website,
        contact_info AS "contactInfo",
        keywords,
        brand_colors AS "brandColors",
        guidelines,
        created_at AS "createdAt",
        updated_at AS "updatedAt";
    `;

    const result = await db.query(queryText, [
      workspaceId,
      name,
      industry,
      targetAudience,
      toneOfVoice,
      primaryLanguage,
      secondaryLanguage,
      writingStyle,
      preferredCta,
      preferredHashtags,
      wordsToAvoid,
      productsServices,
      website,
      contactInfo,
      JSON.stringify(keywords),
      JSON.stringify(brandColors),
      guidelines
    ]);

    return result.rows[0];
  }

  /**
   * Update an existing brand profile in the workspace.
   */
  async updateBrandProfile(workspaceId, id, data) {
    if (!workspaceId || !id) return null;

    if (!db.isConfigured()) {
      const existing = memoryStore.get(id);
      if (!existing || existing.workspaceId !== workspaceId) return null;

      const updated = {
        ...existing,
        name: data.name !== undefined ? data.name : (data.brandName !== undefined ? data.brandName : existing.name),
        brandName: data.name !== undefined ? data.name : (data.brandName !== undefined ? data.brandName : existing.brandName),
        industry: data.industry !== undefined ? data.industry : existing.industry,
        targetAudience: data.targetAudience !== undefined ? data.targetAudience : (data.target_audience !== undefined ? data.target_audience : existing.targetAudience),
        toneOfVoice: data.toneOfVoice !== undefined ? data.toneOfVoice : (data.brandTone !== undefined ? data.brandTone : existing.toneOfVoice),
        primaryLanguage: data.primaryLanguage !== undefined ? data.primaryLanguage : existing.primaryLanguage,
        secondaryLanguage: data.secondaryLanguage !== undefined ? data.secondaryLanguage : existing.secondaryLanguage,
        writingStyle: data.writingStyle !== undefined ? data.writingStyle : existing.writingStyle,
        preferredCta: data.preferredCta !== undefined ? data.preferredCta : existing.preferredCta,
        preferredHashtags: data.preferredHashtags !== undefined ? data.preferredHashtags : existing.preferredHashtags,
        wordsToAvoid: data.wordsToAvoid !== undefined ? data.wordsToAvoid : existing.wordsToAvoid,
        productsServices: data.productsServices !== undefined ? data.productsServices : existing.productsServices,
        website: data.website !== undefined ? data.website : existing.website,
        contactInfo: data.contactInfo !== undefined ? data.contactInfo : existing.contactInfo,
        keywords: data.keywords !== undefined ? data.keywords : existing.keywords,
        brandColors: data.brandColors !== undefined ? data.brandColors : existing.brandColors,
        guidelines: data.guidelines !== undefined ? data.guidelines : (data.businessDescription !== undefined ? data.businessDescription : existing.guidelines),
        updatedAt: new Date().toISOString()
      };
      memoryStore.set(id, updated);
      return { ...updated };
    }

    const existing = await this.getBrandProfileById(workspaceId, id);
    if (!existing) return null;

    const name = data.name !== undefined ? data.name : (data.brandName !== undefined ? data.brandName : existing.name);
    const industry = data.industry !== undefined ? data.industry : existing.industry;
    const targetAudience = data.targetAudience !== undefined ? data.targetAudience : (data.target_audience !== undefined ? data.target_audience : existing.targetAudience);
    const toneOfVoice = data.toneOfVoice !== undefined ? data.toneOfVoice : (data.brandTone !== undefined ? data.brandTone : existing.toneOfVoice);
    const primaryLanguage = data.primaryLanguage !== undefined ? data.primaryLanguage : existing.primaryLanguage;
    const secondaryLanguage = data.secondaryLanguage !== undefined ? data.secondaryLanguage : existing.secondaryLanguage;
    const writingStyle = data.writingStyle !== undefined ? data.writingStyle : existing.writingStyle;
    const preferredCta = data.preferredCta !== undefined ? data.preferredCta : existing.preferredCta;
    const preferredHashtags = data.preferredHashtags !== undefined ? data.preferredHashtags : existing.preferredHashtags;
    const wordsToAvoid = data.wordsToAvoid !== undefined ? data.wordsToAvoid : existing.wordsToAvoid;
    const productsServices = data.productsServices !== undefined ? data.productsServices : existing.productsServices;
    const website = data.website !== undefined ? data.website : existing.website;
    const contactInfo = data.contactInfo !== undefined ? data.contactInfo : existing.contactInfo;
    const keywords = JSON.stringify(data.keywords !== undefined ? data.keywords : existing.keywords);
    const brandColors = JSON.stringify(data.brandColors !== undefined ? data.brandColors : existing.brandColors);
    const guidelines = data.guidelines !== undefined ? data.guidelines : (data.businessDescription !== undefined ? data.businessDescription : existing.guidelines);

    const queryText = `
      UPDATE brand_profiles SET
        name = $3,
        industry = $4,
        target_audience = $5,
        tone_of_voice = $6,
        primary_language = $7,
        secondary_language = $8,
        writing_style = $9,
        preferred_cta = $10,
        preferred_hashtags = $11,
        words_to_avoid = $12,
        products_services = $13,
        website = $14,
        contact_info = $15,
        keywords = $16::jsonb,
        brand_colors = $17::jsonb,
        guidelines = $18,
        updated_at = NOW()
      WHERE workspace_id = $1 AND id = $2
      RETURNING 
        id,
        workspace_id AS "workspaceId",
        name,
        name AS "brandName",
        industry,
        target_audience AS "targetAudience",
        tone_of_voice AS "toneOfVoice",
        primary_language AS "primaryLanguage",
        secondary_language AS "secondaryLanguage",
        writing_style AS "writingStyle",
        preferred_cta AS "preferredCta",
        preferred_hashtags AS "preferredHashtags",
        words_to_avoid AS "wordsToAvoid",
        products_services AS "productsServices",
        website,
        contact_info AS "contactInfo",
        keywords,
        brand_colors AS "brandColors",
        guidelines,
        created_at AS "createdAt",
        updated_at AS "updatedAt";
    `;

    const result = await db.query(queryText, [
      workspaceId,
      id,
      name,
      industry,
      targetAudience,
      toneOfVoice,
      primaryLanguage,
      secondaryLanguage,
      writingStyle,
      preferredCta,
      preferredHashtags,
      wordsToAvoid,
      productsServices,
      website,
      contactInfo,
      keywords,
      brandColors,
      guidelines
    ]);

    return result.rows[0] || null;
  }

  /**
   * Delete brand profile by ID from workspace.
   */
  async deleteBrandProfile(workspaceId, id) {
    if (!workspaceId || !id) return false;

    if (!db.isConfigured()) {
      const existing = memoryStore.get(id);
      if (existing && existing.workspaceId === workspaceId) {
        memoryStore.delete(id);
        return true;
      }
      return false;
    }

    const queryText = `
      DELETE FROM brand_profiles
      WHERE workspace_id = $1 AND id = $2
      RETURNING id;
    `;

    const result = await db.query(queryText, [workspaceId, id]);
    return result.rowCount > 0;
  }
}

module.exports = new BrandProfileService();
